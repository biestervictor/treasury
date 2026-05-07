package org.example.treasury.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.example.treasury.model.CollectorCoinPrice;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.model.CollectorScraperRun;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;
import org.example.treasury.repository.CollectorCoinPriceRepository;
import org.example.treasury.repository.CollectorScraperRunRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestriert die Preisermittlung für Sammlermünzen aus vier externen Quellen:
 * MA-Shops, eBay.de (Browse API, aktive Angebote), gold.de (Preisvergleich) und Numista-API.
 *
 * <p>Jede Quelle kann separat oder gemeinsam getriggert werden.
 * Die ermittelten Preise werden in {@code collectorCoinPrice} gespeichert
 * und dienen als Grundlage für den Preisverlauf-Chart je Münze.
 * Preise unterhalb des Materialwertes (Spot) werden verworfen.</p>
 */
@Service
public class CollectorCoinPricingService {

  private static final Logger log = LoggerFactory.getLogger(CollectorCoinPricingService.class);

  /** Regex für EUR-Preise im Format "45,00" / "1.234,56" / "45.00" / "1234.56". */
  private static final Pattern EUR_PRICE = Pattern.compile(
      "(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})");

  /**
   * Erkennt explizite Gewichtsangaben in Gramm wie "31g", "15.5 g", "31,1g" im Suchterm.
   * Verhindert Falsch-Treffer durch Wortenden wie "farbig", "fünfzig" etc.
   */
  private static final Pattern WEIGHT_G_IN_TEXT = Pattern.compile("\\d+[.,]?\\d*\\s*g\\b");

  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

  private static final double TROY_OZ_GRAMS = 31.1034768;

  // Standard troy-oz-Nennwerte für die Suchbegriff-Anreicherung
  private static final double[] OZ_VALUES =
      {1.0 / 20, 1.0 / 10, 1.0 / 4, 1.0 / 2, 1.0, 2.0, 5.0};
  private static final String[] OZ_LABELS =
      {"1/20oz", "1/10oz", "1/4oz", "1/2oz", "1oz", "2oz", "5oz"};
  private static final double OZ_TOLERANCE_GRAMS = 0.5;

  /**
   * Extrahiert Produktpreis + Titel aus dem Shopify web-pixels-manager-setup Script-Block.
   * Die Produktdaten sind als JSON innerhalb eines JS-Strings eingebettet, d.h. alle
   * Anführungszeichen sind als \" escaped. Das Muster erlaubt optionale Backslashes vor
   * jedem Anführungszeichen, um sowohl die escaped als auch die direkte Form zu unterstützen.
   * Erwartete HTML-Sequenz (escaped):
   * \"price\":{\"amount\":XX.XX,\"currencyCode\":\"EUR\"},\"product\":{\"title\":\"TITEL\"
   */
  private static final Pattern GSA_PRODUCT = Pattern.compile(
      "\\\\?\"price\\\\?\":\\{\\\\?\"amount\\\\?\":(\\d+(?:\\.\\d+)?),"
          + "\\\\?\"currencyCode\\\\?\":\\\\?\"EUR\\\\?\"\\},"
          + "\\\\?\"product\\\\?\":\\{\\\\?\"title\\\\?\":\\\\?\"([^\"\\\\]+)\\\\?\"");

  /** Tracking-State für den Gesamt-Scraper-Lauf (Alle Quellen). */
  private final AtomicBoolean allScraperRunning = new AtomicBoolean(false);
  private final AtomicInteger completedCount = new AtomicInteger(0);
  private volatile String currentSourceName = null;

  /**
   * Per-Münze Scraper-Status:
   * Key = metalId, Value = JSON-String mit Ergebnissen oder "running".
   */
  private final java.util.concurrent.ConcurrentHashMap<String, String> metalScrapeStatus =
      new java.util.concurrent.ConcurrentHashMap<>();

  private final PreciousMetalRepository preciousMetalRepository;
  private final CollectorCoinPriceRepository collectorCoinPriceRepository;
  private final CollectorScraperRunRepository scraperRunRepository;
  private final Clock clock;
  private final AppConfigService appConfigService;

  /**
   * Konstruktor (Produktion).
   *
   * @param preciousMetalRepository      Münz-Repository
   * @param collectorCoinPriceRepository Preisverlauf-Repository
   * @param appConfigService             App-Konfigurationsservice (für API-Keys)
   * @param scraperRunRepository         Scraper-Run-Repository
   */
  @Autowired
  public CollectorCoinPricingService(PreciousMetalRepository preciousMetalRepository,
                                     CollectorCoinPriceRepository collectorCoinPriceRepository,
                                     AppConfigService appConfigService,
                                     CollectorScraperRunRepository scraperRunRepository) {
    this(preciousMetalRepository, collectorCoinPriceRepository, appConfigService,
        scraperRunRepository, Clock.systemUTC());
  }

  /** Test-Konstruktor (ohne scraperRunRepository). */
  CollectorCoinPricingService(PreciousMetalRepository preciousMetalRepository,
                               CollectorCoinPriceRepository collectorCoinPriceRepository,
                               AppConfigService appConfigService,
                               Clock clock) {
    this(preciousMetalRepository, collectorCoinPriceRepository, appConfigService, null, clock);
  }

  CollectorCoinPricingService(PreciousMetalRepository preciousMetalRepository,
                               CollectorCoinPriceRepository collectorCoinPriceRepository,
                               AppConfigService appConfigService,
                               CollectorScraperRunRepository scraperRunRepository,
                               Clock clock) {
    this.preciousMetalRepository = preciousMetalRepository;
    this.collectorCoinPriceRepository = collectorCoinPriceRepository;
    this.appConfigService = appConfigService;
    this.scraperRunRepository = scraperRunRepository;
    this.clock = clock;
  }

  /**
   * Aktualisiert Preise aus allen vier Quellen sequenziell.
   * Wenn bereits ein Lauf aktiv ist, wird der Aufruf ignoriert.
   *
   * @return alle neu gespeicherten Preis-Einträge (leer wenn bereits läuft)
   */
  public List<CollectorCoinPrice> updateFromAllSources() {
    if (!allScraperRunning.compareAndSet(false, true)) {
      log.warn("Scraper läuft bereits – neuer Trigger wird ignoriert.");
      return List.of();
    }
    completedCount.set(0);
    currentSourceName = null;
    try {
      List<CollectorCoinPrice> all = new ArrayList<>();
      for (CollectorCoinPriceSource source : CollectorCoinPriceSource.values()) {
        currentSourceName = source.getDisplayName();
        try {
          all.addAll(updateFromSource(source));
        } catch (Exception e) {
          log.warn("Fehler bei Quelle {}: {}", source, e.getMessage());
        } finally {
          completedCount.incrementAndGet();
        }
      }
      return all;
    } finally {
      currentSourceName = null;
      allScraperRunning.set(false);
    }
  }

  /**
   * Gibt zurück ob der Gesamt-Scraper-Lauf aktuell aktiv ist.
   *
   * @return {@code true} wenn aktiv
   */
  public boolean isAllScraperRunning() {
    return allScraperRunning.get();
  }

  /**
   * Anzahl der bereits abgeschlossenen Quellen im aktuellen Gesamt-Lauf.
   *
   * @return abgeschlossene Quellen
   */
  public int getCompletedCount() {
    return completedCount.get();
  }

  /**
   * Gesamtzahl der Quellen.
   *
   * @return Anzahl Quellen
   */
  public int getTotalSources() {
    return CollectorCoinPriceSource.values().length;
  }

  /**
   * Anzeigename der aktuell laufenden Quelle, oder {@code null}.
   *
   * @return aktueller Quellenname
   */
  public String getCurrentSourceName() {
    return currentSourceName;
  }

  /**
   * Prüft ob ein Einzelmünzen-Scraper für die angegebene Münze bereits läuft.
   *
   * @param metalId MongoDB-ID der Münze
   * @return {@code true} wenn aktiv
   */
  public boolean isMetalScrapeRunning(String metalId) {
    return "running".equals(metalScrapeStatus.get(metalId));
  }

  /**
   * Liefert den aktuellen Scraper-Status für eine Münze als Map (für JSON-Serialisierung).
   *
   * @param metalId MongoDB-ID der Münze
   * @return Map mit running, results
   */
  public Map<String, Object> getMetalScrapeStatus(String metalId) {
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    String raw = metalScrapeStatus.get(metalId);
    out.put("running", "running".equals(raw));
    out.put("done", raw != null && !"running".equals(raw));
    out.put("results", raw != null && !"running".equals(raw) ? raw : null);
    return out;
  }

  /**
   * Scrapt eine einzelne Münze über alle HTTP-Quellen und speichert die Ergebnisse.
   * Aktualisiert {@code metalScrapeStatus} während des Laufs.
   *
   * @param metalId MongoDB-ID der Münze
   */
  public void updateFromAllSourcesForMetal(String metalId) {
    PreciousMetal metal = preciousMetalRepository.findById(metalId).orElse(null);
    if (metal == null) {
      return;
    }
    metalScrapeStatus.put(metalId, "running");
    List<Map<String, Object>> sourceResults = new ArrayList<>();
    try {
      HttpClient http = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

      for (CollectorCoinPriceSource source : CollectorCoinPriceSource.values()) {
        Map<String, Object> srcResult = new java.util.LinkedHashMap<>();
        srcResult.put("source", source.getDisplayName());
        CollectorCoinPrice price = null;
        try {
          price = switch (source) {
            case MA_SHOPS -> scrapeGoldSilberAnlage(http, metal, buildGsaSearchTerm(metal));
            case SILBER_CORNER -> scrapeSilberCorner(http, metal);
            case COININVEST -> scrapeSingleGoldDe(http, metal);
            default -> null; // eBay/Numista/Playwright sources skipped in single-metal mode
          };
          if (price != null) {
            collectorCoinPriceRepository.save(price);
          }
        } catch (Exception e) {
          log.warn("Einzelmünzen-Scraper {} – {}: {}", source, metal.getName(), e.getMessage());
        }
        srcResult.put("found", price != null);
        srcResult.put("price", price != null ? price.getPriceEur() : null);
        sourceResults.add(srcResult);
      }
    } finally {
      // Serialize results to JSON string for status polling
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < sourceResults.size(); i++) {
        if (i > 0) sb.append(",");
        Map<String, Object> r = sourceResults.get(i);
        sb.append("{\"source\":\"").append(r.get("source")).append("\"");
        sb.append(",\"found\":").append(r.get("found"));
        Object p = r.get("price");
        sb.append(",\"price\":").append(p != null ? p : "null");
        sb.append("}");
      }
      sb.append("]");
      metalScrapeStatus.put(metalId, sb.toString());
    }
  }

  /**
   * Aktualisiert Preise von einer bestimmten Quelle für alle Münzen.
   *
   * @param source Preisquelle
   * @return neu gespeicherte Preis-Einträge
   */
  public List<CollectorCoinPrice> updateFromSource(CollectorCoinPriceSource source) {
    List<PreciousMetal> metals = preciousMetalRepository.findAllByImportedAtIsNotNull();
    log.info("CollectorCoinPricingService: starte Quelle {} für {} Münzen", source, metals.size());

    if (source == CollectorCoinPriceSource.NUMISTA) {
      return updateFromNumista(metals);
    }
    if (source == CollectorCoinPriceSource.EBAY) {
      return updateFromEbay(metals);
    }
    if (source == CollectorCoinPriceSource.EBAY_SOLD) {
      return updateFromEbaySoldHttp(metals);
    }
    if (source == CollectorCoinPriceSource.COININVEST) {
      return updateFromGoldDe(metals);
    }
    if (source == CollectorCoinPriceSource.MA_SHOPS) {
      return updateFromGoldSilberAnlage(metals);
    }
    if (source == CollectorCoinPriceSource.SILBER_CORNER) {
      return updateFromSilberCorner(metals);
    }
    if (source == CollectorCoinPriceSource.SILBERLING) {
      return updateFromSilberling(metals);
    }

    // Playwright-basierte Quellen (Fallback, kein aktiver Source-Typ)
    Map<String, Double> prevPrices = loadPreviousPrices(metals, source);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions()
              .setHeadless(true)
              .setArgs(java.util.List.of(
                  "--disable-dev-shm-usage",
                  "--no-sandbox",
                  "--disable-gpu"
              )));
      BrowserContext context = browser.newContext(
          new Browser.NewContextOptions()
              .setUserAgent(USER_AGENT)
              .setIgnoreHTTPSErrors(true));
      try {
        for (PreciousMetal metal : metals) {
          String term = effectiveSearchTerm(metal);
          CollectorCoinPrice entry = null;
          try {
            entry = scrapeWithPlaywright(source, context, metal, term);
            if (entry != null) {
              results.add(collectorCoinPriceRepository.save(entry));
              log.info("  {} – {}: {} EUR", source, metal.getName(),
                  String.format("%.2f", entry.getPriceEur()));
            }
          } catch (Exception e) {
            log.warn("  {} – {} Scrape fehlgeschlagen: {}", source, metal.getName(),
                e.getMessage());
          }
          runEntries.add(CollectorScraperRun.Entry.builder()
              .metalId(metal.getId())
              .metalName(metal.getName())
              .searchTerm(term)
              .success(entry != null)
              .priceEur(entry != null ? entry.getPriceEur() : null)
              .previousPriceEur(prevPrices.get(metal.getId()))
              .build());
          sleepMs(1500);
        }
      } finally {
        browser.close();
      }
    }

    saveScraperRun(source, metals.size(), results.size(), runEntries);
    log.info("CollectorCoinPricingService: {} fertig – {} Einträge gespeichert",
        source, results.size());
    return results;
  }

  /**
   * Liefert die vollständige Preisverlauf-Historie für eine Münze.
   *
   * @param metalId MongoDB-ID der Münze
   * @return chronologisch sortierte Preis-Einträge
   */
  public List<CollectorCoinPrice> getPriceHistory(String metalId) {
    return collectorCoinPriceRepository.findByPreciousMetalIdOrderByTimestampAsc(metalId);
  }

  /**
   * Liefert den abgeleiteten Sammlerwert pro Münze als Durchschnitt der jeweils
   * aktuellsten Preise aller Quellen — gefiltert: Preise unterhalb des Materialwerts
   * (Spot-Preis auf Basis Gewicht + Metalltyp) werden ausgeschlossen.
   *
   * @param goldPerOz    aktueller Goldpreis EUR/oz
   * @param silverPerOz  aktueller Silberpreis EUR/oz
   * @return Map: PreciousMetal-ID → ermittelter Sammlerwert in EUR
   */
  public Map<String, Double> getLatestCollectorPricePerMetal(double goldPerOz,
                                                              double silverPerOz) {
    Map<String, PreciousMetal> metalById = preciousMetalRepository.findAll()
        .stream().collect(Collectors.toMap(PreciousMetal::getId, m -> m));

    List<CollectorCoinPrice> all = new ArrayList<>(collectorCoinPriceRepository.findAll());
    all.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

    // Neuester Preis pro Münze+Quelle (sortiert desc → putIfAbsent nimmt den ersten = neuesten)
    Map<String, Map<CollectorCoinPriceSource, Double>> latestPerMetalSource = new HashMap<>();
    for (CollectorCoinPrice p : all) {
      if (p.getPreciousMetalId() == null || p.getTimestamp() == null) {
        continue;
      }
      latestPerMetalSource
          .computeIfAbsent(p.getPreciousMetalId(), k -> new HashMap<>())
          .putIfAbsent(p.getSource(), p.getPriceEur());
    }

    Map<String, Double> result = new HashMap<>();
    latestPerMetalSource.forEach((metalId, sourceMap) -> {
      PreciousMetal metal = metalById.get(metalId);
      double spotValue = computeSpotValue(metal, goldPerOz, silverPerOz);

      OptionalDouble avg = sourceMap.values().stream()
          .filter(v -> v > 0 && v >= spotValue)
          .mapToDouble(d -> d)
          .average();
      if (avg.isPresent()) {
        result.put(metalId, avg.getAsDouble());
      }
    });
    return result;
  }

  /**
   * Löscht alle gespeicherten Sammelpreis-Einträge und Scraper-Läufe.
   * Nur im Dev-Modus verwenden.
   */
  public void deleteAllPricesAndRuns() {
    collectorCoinPriceRepository.deleteAll();
    if (scraperRunRepository != null) {
      scraperRunRepository.deleteAll();
    }
    log.info("Alle CollectorCoinPrice-Einträge und CollectorScraperRun-Dokumente gelöscht (Dev-Reset).");
  }

  /**
   * Gibt alle gespeicherten Scraper-Läufe zurück, neueste zuerst.
   *
   * @return Liste der CollectorScraperRun-Einträge
   */
  public List<CollectorScraperRun> getScraperHistory() {
    if (scraperRunRepository == null) {
      return List.of();
    }
    return scraperRunRepository.findAllByOrderByTimestampDesc();
  }

  /**
   * Gibt den neuesten Scraper-Lauf pro Quelle zurück.
   *
   * @return Map: Quelle → neuester CollectorScraperRun
   */
  public Map<CollectorCoinPriceSource, CollectorScraperRun> getLatestRunPerSource() {
    Map<CollectorCoinPriceSource, CollectorScraperRun> result = new HashMap<>();
    if (scraperRunRepository == null) {
      return result;
    }
    for (CollectorCoinPriceSource source : CollectorCoinPriceSource.values()) {
      List<CollectorScraperRun> runs =
          scraperRunRepository.findBySourceOrderByTimestampDesc(source);
      if (!runs.isEmpty()) {
        result.put(source, runs.get(0));
      }
    }
    return result;
  }

  // ─── Playwright-basierte Scrapers ────────────────────────────────────────

  private CollectorCoinPrice scrapeWithPlaywright(CollectorCoinPriceSource source,
                                                    BrowserContext context,
                                                    PreciousMetal metal,
                                                    String term) {
    return switch (source) {
      case MA_SHOPS -> scrapeMaShops(context, metal, term);
      default -> null;
    };
  }

  // MA-Shops ──────────────────────────────────────────────────────────────

  private CollectorCoinPrice scrapeMaShops(BrowserContext context,
                                             PreciousMetal metal,
                                             String term) {
    // Ohne catid-Filter – catid=12 lieferte für nicht-spezifische Begriffe immer
    // denselben günstigsten Silber-Coin (~27.95 EUR), unabhängig vom Suchbegriff.
    // Stattdessen: Mindestpreis 25 EUR als Untergrenze gegen Fremdmünzen.
    String url = "https://www.ma-shops.de/shops/search.php?l=de&s=" + encode(term);
    Page page = context.newPage();
    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(20000));
      page.waitForTimeout(2000);

      // "Keine Treffer"-Check
      String bodyText = page.innerText("body");
      if (bodyText != null && (bodyText.contains("keine Treffer")
          || bodyText.contains("Kein Ergebnis")
          || bodyText.contains("no results"))) {
        log.debug("MA-Shops: keine Treffer für '{}'", term);
        return null;
      }

      List<ElementHandle> priceCells = page.querySelectorAll("td.spx-price");
      // Wenn mehr als 10 Preiszellen: generische Kategorieseite, kein spezifischer Treffer
      if (priceCells.size() > 10) {
        log.debug("MA-Shops: {} Treffer – zu viele für spezifischen Coin, überspringe '{}'",
            priceCells.size(), term);
        return null;
      }
      List<ElementHandle> firstPriceSpans = new ArrayList<>();
      for (ElementHandle cell : priceCells) {
        ElementHandle firstSpan = cell.querySelector("span.price");
        if (firstSpan != null) {
          firstPriceSpans.add(firstSpan);
        }
      }
      OptionalDouble price = extractLowestPriceWithVariance(firstPriceSpans, 25.0);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot");
      }

      List<ElementHandle> cardSpans = page.querySelectorAll("span.itemPrice span.curr1.price");
      if (cardSpans.size() > 10) {
        log.debug("MA-Shops: {} Galerie-Treffer – zu viele, überspringe '{}'",
            cardSpans.size(), term);
        return null;
      }
      price = extractLowestPriceWithVariance(cardSpans, 25.0);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot – Galerie");
      }

      log.debug("MA-Shops: keine verwertbaren Treffer für '{}'", term);
      return null;
    } finally {
      closePage(page);
    }
  }

  // eBay Browse API (OAuth2 client_credentials) ─────────────────────────────

  /** Cached eBay OAuth2 access token. */
  private volatile String ebayAccessToken = null;

  /** Epoch-second at which the cached token expires. */
  private volatile long ebayTokenExpiresAt = 0L;

  private List<CollectorCoinPrice> updateFromEbay(List<PreciousMetal> metals) {
    String appId = appConfigService.get(AppConfigService.KEY_EBAY_APP_ID);
    String certId = appConfigService.get(AppConfigService.KEY_EBAY_CERT_ID);
    if (appId.isBlank() || certId.isBlank()) {
      log.warn("eBay App ID oder Cert ID nicht konfiguriert. Überspringe eBay.");
      return List.of();
    }

    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    String token;
    try {
      token = getEbayToken(http, appId, certId);
    } catch (Exception e) {
      log.warn("eBay Token-Abruf fehlgeschlagen: {}", e.getMessage());
      return List.of();
    }
    if (token == null || token.isBlank()) {
      log.warn("eBay: leeres Access-Token – überspringe Batch");
      return List.of();
    }

    Map<String, Double> prevPrices = loadPreviousPrices(metals, CollectorCoinPriceSource.EBAY);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String term = effectiveEbaySearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = fetchEbayBrowsePrice(http, metal, term, token);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  EBAY – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1000);
      } catch (Exception e) {
        log.warn("  EBAY – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(term)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.EBAY, metals.size(), results.size(), runEntries);
    log.info("CollectorCoinPricingService: EBAY fertig – {} Einträge gespeichert", results.size());
    return results;
  }

  /**
   * Returns a valid eBay OAuth2 access token, fetching a new one if the cached one has expired.
   *
   * @param http   the HTTP client
   * @param appId  the eBay App ID (Client ID)
   * @param certId the eBay Cert ID (Client Secret)
   * @return access token string, or {@code null} on failure
   * @throws IOException          on network error
   * @throws InterruptedException if interrupted
   */
  private String getEbayToken(HttpClient http, String appId, String certId)
      throws IOException, InterruptedException {
    long nowSec = Instant.now(clock).getEpochSecond();
    if (ebayAccessToken != null && nowSec < ebayTokenExpiresAt - 60) {
      return ebayAccessToken;
    }

    String credentials = Base64.getEncoder().encodeToString(
        (appId + ":" + certId).getBytes(StandardCharsets.UTF_8));
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://api.ebay.com/identity/v1/oauth2/token"))
        .header("Authorization", "Basic " + credentials)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(
            "grant_type=client_credentials&scope="
                + encode("https://api.ebay.com/oauth/api_scope")))
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.warn("eBay Token HTTP {}: {}", resp.statusCode(),
          resp.body().substring(0, Math.min(300, resp.body().length())));
      return null;
    }

    Matcher tokenMatcher = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
        .matcher(resp.body());
    if (!tokenMatcher.find()) {
      log.warn("eBay Token-Antwort enthält kein access_token");
      return null;
    }
    String token = tokenMatcher.group(1);

    Matcher expMatcher = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)").matcher(resp.body());
    long expiresIn = expMatcher.find() ? Long.parseLong(expMatcher.group(1)) : 7200L;

    ebayAccessToken = token;
    ebayTokenExpiresAt = nowSec + expiresIn;
    log.info("eBay OAuth2-Token abgerufen, gültig für {}s", expiresIn);
    return token;
  }

  /**
   * Fetches the average price of the top-5 active eBay listings via Browse API.
   * Returns {@code null} if no listing is found or all prices are below the spot value.
   *
   * @param http   the HTTP client
   * @param metal  the coin to price
   * @param term   the (enriched) search term
   * @param token  the OAuth2 Bearer token
   * @return a {@link CollectorCoinPrice} entry, or {@code null} if no usable result
   * @throws IOException          on network error
   * @throws InterruptedException if interrupted
   */
  private CollectorCoinPrice fetchEbayBrowsePrice(HttpClient http,
                                                   PreciousMetal metal,
                                                   String term,
                                                   String token)
      throws IOException, InterruptedException {
    String searchUrl = "https://api.ebay.com/buy/browse/v1/item_summary/search"
        + "?q=" + encode(term)
        + "&filter=" + encode("categoryIds:{11116}")
        + "&limit=10";

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(searchUrl))
        .header("Authorization", "Bearer " + token)
        .header("X-EBAY-C-MARKETPLACE-ID", "EBAY_DE")
        .header("User-Agent", "treasury-bot/1.0")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.warn("eBay Browse API HTTP {} für '{}': {}", resp.statusCode(), term,
          resp.body().substring(0, Math.min(300, resp.body().length())));
      return null;
    }

    // Extract: "price":{"value":"45.00","currency":"EUR"}
    Matcher m = Pattern.compile("\"price\"\\s*:\\s*\\{[^}]*?\"value\"\\s*:\\s*\"([\\d.]+)\"")
        .matcher(resp.body());
    List<Double> prices = new ArrayList<>();
    while (m.find()) {
      try {
        double p = Double.parseDouble(m.group(1));
        if (p > 0) {
          prices.add(p);
        }
      } catch (NumberFormatException ignored) {
        // ignored
      }
    }

    if (prices.isEmpty()) {
      log.debug("eBay Browse: keine Preise für '{}'", term);
      return null;
    }

    // Materialwert berechnen und Preise darunter herausfiltern
    double spotValue = computeSpotValue(metal, 0, 0); // spot ohne aktuelle Preise → 0 Fallback
    // Spot ohne Preisangabe → wir filtern hier konservativ nur offensichtlichen Schrott (< 1 EUR)
    // Die echte Spot-Filterung erfolgt in getLatestCollectorPricePerMetal() mit aktuellen Kursen.
    List<Double> filtered = prices.stream().filter(p -> p > 1.0).toList();
    if (filtered.isEmpty()) {
      log.debug("eBay Browse: alle Preise unter 1 EUR für '{}'", term);
      return null;
    }

    int count = Math.min(5, filtered.size());
    double avg = filtered.stream().limit(count).mapToDouble(d -> d).average().orElse(0);
    if (avg <= 0) {
      return null;
    }

    String sourceUrl = "https://www.ebay.de/sch/i.html?_nkw=" + encode(term) + "&_sacat=11116";
    return buildEntry(metal, CollectorCoinPriceSource.EBAY, avg, sourceUrl,
        "Ø " + count + " aktive Angebote (Browse API)");
  }

  // eBay verkaufte Artikel – HTTP-Scraping (kein Playwright) ─────────────────

  /** Regex zum Extrahieren des eBay-Preistexts aus dem Suchergebnis-HTML. */
  private static final Pattern EBAY_SOLD_PRICE = Pattern.compile(
      "s-item__price[^>]*>\\s*([\\d.,]+)\\s*(?:EUR|€)",
      Pattern.CASE_INSENSITIVE);

  /**
   * Scrapt verkaufte eBay-Angebote via einfachem HTTP-Request gegen die SSR-Suchergebnisseite.
   * Kein Playwright, kein OAuth – eBay liefert Preise im initialen HTML.
   */
  private List<CollectorCoinPrice> updateFromEbaySoldHttp(List<PreciousMetal> metals) {
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    Map<String, Double> prevPrices =
        loadPreviousPrices(metals, CollectorCoinPriceSource.EBAY_SOLD);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String term = effectiveEbaySearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = fetchEbaySoldHttp(http, metal, term);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  EBAY_SOLD – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1500);
      } catch (EbayBlockedException e) {
        log.warn("EBAY_SOLD: eBay blockiert Zugriff auf Completed-Items ({}) – "
            + "Batch wird übersprungen. Alle verbleibenden Münzen erhalten keinen Preis.", e.getMessage());
        // restliche Münzen ohne Sleep als "nicht gefunden" eintragen
        runEntries.add(CollectorScraperRun.Entry.builder()
            .metalId(metal.getId()).metalName(metal.getName()).searchTerm(term)
            .success(false).build());
        for (int i = metals.indexOf(metal) + 1; i < metals.size(); i++) {
          PreciousMetal m2 = metals.get(i);
          runEntries.add(CollectorScraperRun.Entry.builder()
              .metalId(m2.getId()).metalName(m2.getName())
              .searchTerm(effectiveEbaySearchTerm(m2))
              .success(false).build());
        }
        break;
      } catch (Exception e) {
        log.warn("  EBAY_SOLD – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(term)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.EBAY_SOLD,
        metals.size(), results.size(), runEntries);
    log.info("CollectorCoinPricingService: EBAY_SOLD fertig – {} Einträge gespeichert",
        results.size());
    return results;
  }

  /**
   * Fetcht die eBay-Verkaufspreise (Completed Items) via HTTP-GET gegen die eBay-Suchseite.
   * eBay liefert die Preise im server-seitig gerendertem HTML ohne JavaScript.
   * Falls eBay Zugriff blockiert (403), wird der gesamte Batch frühzeitig beendet.
   *
   * @param http  der HTTP-Client
   * @param metal die Münze
   * @param term  der angereicherte Suchbegriff
   * @return CollectorCoinPrice oder {@code null} wenn kein Treffer oder blockiert
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice fetchEbaySoldHttp(HttpClient http,
                                                PreciousMetal metal,
                                                String term)
      throws IOException, InterruptedException {
    String url = "https://www.ebay.de/sch/i.html"
        + "?_nkw=" + encode(term)
        + "&_sacat=11116"
        + "&LH_Complete=1"
        + "&LH_Sold=1"
        + "&_sop=13";

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.5")
        .header("Accept-Encoding", "gzip, deflate, br")
        .header("Referer", "https://www.google.de/")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "cross-site")
        .header("Upgrade-Insecure-Requests", "1")
        .GET()
        .timeout(Duration.ofSeconds(20))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() == 403 || resp.statusCode() == 429) {
      throw new EbayBlockedException("HTTP " + resp.statusCode());
    }
    if (resp.statusCode() != 200) {
      log.debug("eBay Sold HTTP {} für '{}'", resp.statusCode(), term);
      return null;
    }

    Matcher m = EBAY_SOLD_PRICE.matcher(resp.body());
    List<Double> prices = new ArrayList<>();
    while (m.find() && prices.size() < 10) {
      String raw = m.group(1);
      if (raw.contains(" bis ")) {
        raw = raw.split(" bis ")[0];
      }
      OptionalDouble p = parseEurValue(raw);
      if (p.isPresent() && p.getAsDouble() > 1.0) {
        prices.add(p.getAsDouble());
      }
    }

    if (prices.isEmpty()) {
      log.debug("eBay Sold HTTP: keine Preise im HTML für '{}'", term);
      return null;
    }

    int count = Math.min(5, prices.size());
    double avg = prices.stream().limit(count).mapToDouble(d -> d).average().orElse(0);
    if (avg <= 0) {
      return null;
    }

    String sourceUrl = "https://www.ebay.de/sch/i.html?_nkw=" + encode(term)
        + "&_sacat=11116&LH_Complete=1&LH_Sold=1";
    return buildEntry(metal, CollectorCoinPriceSource.EBAY_SOLD, avg, sourceUrl,
        "Ø " + count + " verkaufte Angebote (HTTP-Scraping)");
  }

  /** Signalisiert, dass eBay den Zugriff blockiert hat (403/429). */
  private static class EbayBlockedException extends RuntimeException {
    EbayBlockedException(String msg) {
      super(msg);
    }
  }

  // gold-silber-anlage.com – HTTP Shopify-Scraper ─────────────────────────

  /**
   * Scrapt Preise von gold-silber-anlage.com (Shopify-Shop) per HTTP-GET.
   * Die Suchergebnis-Seite enthält alle Produkte als eingebettetes JSON.
   *
   * @param metals Münzliste
   * @return neu gespeicherte Preis-Einträge
   */
  private List<CollectorCoinPrice> updateFromGoldSilberAnlage(List<PreciousMetal> metals) {
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    Map<String, Double> prevPrices = loadPreviousPrices(metals, CollectorCoinPriceSource.MA_SHOPS);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String searchTerm = buildGsaSearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = scrapeGoldSilberAnlage(http, metal, searchTerm);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  MA_SHOPS(gold-silber-anlage.com) – {}: {} EUR",
              metal.getName(), String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1000);
      } catch (Exception e) {
        log.warn("  gold-silber-anlage.com – {} fehlgeschlagen: {}",
            metal.getName(), e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(searchTerm)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.MA_SHOPS,
        metals.size(), results.size(), runEntries);
    log.info("CollectorCoinPricingService: MA_SHOPS(gold-silber-anlage.com) fertig"
        + " – {} Einträge gespeichert", results.size());
    return results;
  }

  /**
   * Fetcht und parst eine gold-silber-anlage.com-Suche.
   * Das Shopify-SSR-HTML enthält alle Produktdaten als eingebettetes JSON-Fragment.
   * Wir filtern nach passendem Metalltyp und Gewichtsklasse (Unzen-Label im Titel).
   *
   * @param http       HTTP-Client
   * @param metal      Münze
   * @param searchTerm Suchbegriff (bereits normalisiert)
   * @return günstigster passender Preis oder {@code null}
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice scrapeGoldSilberAnlage(HttpClient http,
                                                     PreciousMetal metal,
                                                     String searchTerm)
      throws IOException, InterruptedException {
    String url = "https://gold-silber-anlage.com/search?q="
        + encode(searchTerm) + "&options%5Bprefix%5D=last&sort_by=price-ascending";

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.5")
        .header("Accept-Encoding", "identity")
        .GET()
        .timeout(Duration.ofSeconds(20))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.warn("gold-silber-anlage HTTP {} für '{}'", resp.statusCode(), searchTerm);
      return null;
    }

    String typeLabel = (metal.getType() == PreciousMetalType.GOLD) ? "GOLD" : "SILBER";
    String ozLabel = gsaOzLabel(metal.getWeightInGrams());

    Matcher m = GSA_PRODUCT.matcher(resp.body());
    Double bestPrice = null;
    String bestTitle = null;
    while (m.find()) {
      double price = Double.parseDouble(m.group(1));
      String title = m.group(2).toUpperCase(Locale.ROOT);
      if (!title.contains(typeLabel)) {
        continue;
      }
      if (ozLabel != null && !title.contains(ozLabel)) {
        continue;
      }
      if (bestPrice == null || price < bestPrice) {
        bestPrice = price;
        bestTitle = m.group(2);
      }
    }

    if (bestPrice == null) {
      String contentEncoding = resp.headers().firstValue("content-encoding").orElse("none");
      String bodySnippet = resp.body().length() > 500
          ? resp.body().substring(0, 500) : resp.body();
      log.warn("gold-silber-anlage: kein Treffer für '{}' (oz={}, type={}) "
              + "| Content-Encoding={} | body[0..500]={}",
          searchTerm, ozLabel, typeLabel, contentEncoding, bodySnippet);
      return null;
    }

    return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS, bestPrice, url, bestTitle);
  }

  /**
   * Baut den Suchbegriff für gold-silber-anlage.com:
   * Ersetzt „farbig" durch „koloriert", behält den restlichen Namen (z.B. „Perth Mint Lunar 2 Hahn koloriert").
   *
   * @param metal Münze
   * @return normalisierter Suchbegriff
   */
  private String buildGsaSearchTerm(PreciousMetal metal) {
    String base = effectiveSearchTerm(metal);
    return base.replace("farbig", "koloriert").replace("Farbig", "koloriert").trim();
  }

  /**
   * Liefert das Unzen-Label wie im gold-silber-anlage.com Produkttitel verwendet.
   *
   * @param grams Gewicht in Gramm
   * @return z.B. "1 UNZE", "1/2 UNZE" oder {@code null} wenn kein passendes Label
   */
  private String gsaOzLabel(double grams) {
    if (grams <= 0) {
      return null;
    }
    if (Math.abs(grams - TROY_OZ_GRAMS) < 2.0) {
      return "1 UNZE";
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 2) < 1.5) {
      return "1/2 UNZE";
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 4) < 1.0) {
      return "1/4 UNZE";
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 10) < 0.5) {
      return "1/10 UNZE";
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 20) < 0.3) {
      return "1/20 UNZE";
    }
    return null;
  }

  // gold.de – HTTP Preisvergleich (kein Playwright) ────────────────────────

  /**
   * Sucht auf gold.de nach einem Münzpreis via zweistufigem HTTP-Scraping:
   * Schritt 1: Suchseite → erste Produkt-URL extrahieren
   * Schritt 2: Produktseite → günstigsten Händlerpreis extrahieren.
   *
   * @param metals Münzliste
   * @return neu gespeicherte Preis-Einträge
   */
  private List<CollectorCoinPrice> updateFromGoldDe(List<PreciousMetal> metals) {
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    Map<String, Double> prevPrices =
        loadPreviousPrices(metals, CollectorCoinPriceSource.COININVEST);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String term = effectiveSearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = fetchGoldDePrice(http, metal, term);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  COININVEST(gold.de) – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1500);
      } catch (Exception e) {
        log.warn("  COININVEST(gold.de) – {} fehlgeschlagen: {}", metal.getName(),
            e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(term)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.COININVEST, metals.size(), results.size(),
        runEntries);
    log.info("CollectorCoinPricingService: COININVEST(gold.de) fertig – {} Einträge gespeichert",
        results.size());
    return results;
  }

  /** Regex für gold.de Produkt-URLs in Suchergebnissen. */
  private static final Pattern GOLD_DE_PRODUCT_URL = Pattern.compile(
      "data-filter=\"produkte\"[\\s\\S]{0,300}?href=\"(https://www\\.gold\\.de/kaufen/[^\"]+)\"",
      Pattern.CASE_INSENSITIVE);

  /** Regex für den Händlerpreis auf einer gold.de Produktseite (preis=83.7200). */
  private static final Pattern GOLD_DE_PREIS = Pattern.compile("(?<![_a-z])preis=([0-9]+\\.[0-9]+)",
      Pattern.CASE_INSENSITIVE);

  /**
   * Scrapt den günstigsten Händlerpreis von gold.de für eine Münze.
   * Schritt 1: Suche auf gold.de → erste Produkt-URL.
   * Schritt 2: Produktseite → extrahiere alle {@code preis=XX.XX} Werte.
   *
   * @param http   HTTP-Client
   * @param metal  Münze
   * @param term   Suchbegriff
   * @return Preis-Eintrag oder {@code null} wenn kein Treffer
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice fetchGoldDePrice(HttpClient http, PreciousMetal metal, String term)
      throws IOException, InterruptedException {
    // Schritt 1: Suche
    String searchUrl = "https://www.gold.de/suche/?q=" + encode(term);
    String searchHtml = goldDeGet(http, searchUrl);
    if (searchHtml == null) {
      return null;
    }

    Matcher urlMatcher = GOLD_DE_PRODUCT_URL.matcher(searchHtml);
    if (!urlMatcher.find()) {
      log.debug("gold.de: kein Produkt-Treffer für '{}'", term);
      return null;
    }
    String productUrl = urlMatcher.group(1);
    log.debug("gold.de: Produkt-URL für '{}' → {}", term, productUrl);

    // Metalltyp-Check: Silbermünze → keine Goldmünzen-/Goldbarren-Seite akzeptieren (und umgekehrt).
    // Verhindert, dass nicht vorhandene Coins auf eine generische Gold- oder Silberseite fallen.
    if (metal.getType() == PreciousMetalType.SILVER
        && (productUrl.contains("goldmuenzen") || productUrl.contains("goldbarren"))) {
      log.debug("gold.de: Silbermünze '{}' hätte Goldseite {} bekommen – verwerfe", term,
          productUrl);
      return null;
    }
    if (metal.getType() == PreciousMetalType.GOLD
        && (productUrl.contains("silbermuenzen") || productUrl.contains("silberbarren"))) {
      log.debug("gold.de: Goldmünze '{}' hätte Silberseite {} bekommen – verwerfe", term,
          productUrl);
      return null;
    }

    sleepMs(500);

    // Schritt 2: Produktseite
    String productHtml = goldDeGet(http, productUrl);
    if (productHtml == null) {
      return null;
    }

    Matcher priceMatcher = GOLD_DE_PREIS.matcher(productHtml);
    List<Double> prices = new ArrayList<>();
    while (priceMatcher.find() && prices.size() < 20) {
      try {
        double p = Double.parseDouble(priceMatcher.group(1));
        if (p > 5.0) {
          prices.add(p);
        }
      } catch (NumberFormatException ignored) {
        // ignorieren
      }
    }

    if (prices.isEmpty()) {
      log.debug("gold.de: keine Preise auf '{}'", productUrl);
      return null;
    }

    double min = prices.stream().mapToDouble(d -> d).min().getAsDouble();

    // Plausibilitätsprüfung: Preis darf nicht mehr als 5× den Spot-Wert betragen.
    // Verhindert, dass eine Kategorieseite (z. B. 1oz-Känguru für einen 1/10oz-Coin) übernommen wird.
    // Approximative Spot-Preise (werden gelegentlich aktualisiert, exakte Filterung
    // erfolgt weiterhin in getLatestCollectorPricePerMetal).
    double approxGoldEurOz = 3100.0;
    double approxSilverEurOz = 31.0;
    double approxSpot = computeSpotValue(metal, approxGoldEurOz, approxSilverEurOz);
    if (approxSpot > 0 && min > approxSpot * 5) {
      log.debug("gold.de: Preis {}  EUR ist {}× approximativer Spot ({} EUR) für '{}' – verwerfe",
          String.format("%.2f", min), String.format("%.1f", min / approxSpot),
          String.format("%.0f", approxSpot), term);
      return null;
    }

    return buildEntry(metal, CollectorCoinPriceSource.COININVEST, min, productUrl,
        "günstigstes Angebot (gold.de)");
  }

  /**
   * Führt einen HTTP-GET gegen gold.de aus und gibt den HTML-Body zurück.
   *
   * @param http HTTP-Client
   * @param url  Ziel-URL
   * @return HTML-Body oder {@code null} bei Fehler
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private String goldDeGet(HttpClient http, String url)
      throws IOException, InterruptedException {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
        .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.5")
        .header("Accept-Encoding", "identity")
        .GET()
        .timeout(Duration.ofSeconds(20))
        .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.debug("gold.de HTTP {} für '{}'", resp.statusCode(), url);
      return null;
    }
    return resp.body();
  }

  // ─── Numista REST-API ────────────────────────────────────────────────────

  private List<CollectorCoinPrice> updateFromNumista(List<PreciousMetal> metals) {
    String numistaApiKey = appConfigService.get(AppConfigService.KEY_NUMISTA_API_KEY);
    if (numistaApiKey.isBlank()) {
      log.warn("Numista-API-Key nicht konfiguriert. Überspringe Numista.");
      return List.of();
    }
    log.info("Numista: starte mit Key (Länge={}, Prefix={}...)",
        numistaApiKey.length(), numistaApiKey.substring(0, Math.min(6, numistaApiKey.length())));

    Map<String, Double> prevPrices = loadPreviousPrices(metals, CollectorCoinPriceSource.NUMISTA);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    for (PreciousMetal metal : metals) {
      String term = effectiveSearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = fetchNumistaPrice(http, metal, term, numistaApiKey);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  NUMISTA – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(500);
      } catch (Exception e) {
        log.warn("  NUMISTA – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(term)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.NUMISTA, metals.size(), results.size(), runEntries);
    return results;
  }

  /**
   * Fetches a collector coin price from the Numista v3 API.
   * Flow: GET /v3/types?q={term} → type_id
   *       GET /v3/types/{type_id}/issues → issue_id (first issue)
   *       GET /v3/types/{type_id}/issues/{issue_id}/prices?currency=EUR → grade prices
   * Best grade selected: unc > au > xf > vf > f > vg > g.
   */
  private CollectorCoinPrice fetchNumistaPrice(HttpClient http,
                                                PreciousMetal metal,
                                                String term,
                                                String numistaApiKey)
      throws IOException, InterruptedException {
    String searchUrl = "https://api.numista.com/v3/types?q="
        + encode(term) + "&lang=en&count=1";
    HttpResponse<String> searchResp = numistaGet(http, searchUrl, numistaApiKey);
    if (searchResp.statusCode() != 200) {
      log.warn("Numista Suche HTTP {} für '{}': {}", searchResp.statusCode(), term,
          searchResp.body().substring(0, Math.min(200, searchResp.body().length())));
      return null;
    }

    Matcher typeMatcher = Pattern.compile(
        "\"types\"[\\s\\S]*?\"id\"\\s*:\\s*(\\d+)").matcher(searchResp.body());
    if (!typeMatcher.find()) {
      log.debug("Numista: kein Typ gefunden für '{}'", term);
      return null;
    }
    String typeId = typeMatcher.group(1);

    String issuesUrl = "https://api.numista.com/v3/types/" + typeId + "/issues";
    HttpResponse<String> issuesResp = numistaGet(http, issuesUrl, numistaApiKey);
    if (issuesResp.statusCode() != 200) {
      log.debug("Numista Issues HTTP {}: typeId={}", issuesResp.statusCode(), typeId);
      return null;
    }

    Matcher issueMatcher = Pattern.compile(
        "\\[\\s*\\{[\\s\\S]*?\"id\"\\s*:\\s*(\\d+)").matcher(issuesResp.body());
    if (!issueMatcher.find()) {
      log.debug("Numista: keine Issues für typeId={}", typeId);
      return null;
    }
    String issueId = issueMatcher.group(1);

    String priceUrl = "https://api.numista.com/v3/types/" + typeId
        + "/issues/" + issueId + "/prices?currency=EUR";
    HttpResponse<String> priceResp = numistaGet(http, priceUrl, numistaApiKey);
    if (priceResp.statusCode() != 200) {
      log.debug("Numista Preise HTTP {}: typeId={}, issueId={}",
          priceResp.statusCode(), typeId, issueId);
      return null;
    }

    double price = extractBestNumistaPrice(priceResp.body());
    if (price <= 0) {
      log.debug("Numista: keine Preisdaten für '{}' (typeId={}, issueId={})",
          term, typeId, issueId);
      return null;
    }

    return buildEntry(metal, CollectorCoinPriceSource.NUMISTA, price,
        "https://en.numista.com/catalogue/pieces" + typeId + ".html",
        "Numista Katalogpreis (typeId=" + typeId + ")");
  }

  /**
   * Sends a GET request to the Numista API with the required headers.
   */
  private HttpResponse<String> numistaGet(HttpClient http, String url, String apiKey)
      throws IOException, InterruptedException {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Numista-API-Key", apiKey)
        .header("User-Agent", "treasury-bot/1.0")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Extracts the best available grade price from the Numista prices response.
   * Grade preference: unc > au > xf > vf > f > vg > g.
   *
   * @param priceJson the JSON response body from the prices endpoint
   * @return the best price in EUR, or -1 if no price is available
   */
  private double extractBestNumistaPrice(String priceJson) {
    String[] grades = {"unc", "au", "xf", "vf", "f", "vg", "g"};
    for (String grade : grades) {
      Matcher m = Pattern.compile(
          "\"grade\"\\s*:\\s*\"" + grade + "\"\\s*,\\s*\"price\"\\s*:\\s*([\\d.]+)")
          .matcher(priceJson);
      if (m.find()) {
        double p = Double.parseDouble(m.group(1));
        if (p > 0) {
          return p;
        }
      }
    }
    return -1;
  }

  // ─── Such-Term Hilfsmethoden ─────────────────────────────────────────────

  private String effectiveSearchTerm(PreciousMetal metal) {
    String term = metal.getCollectorSearchTerm();
    if (term != null && !term.isBlank()) {
      return term.trim();
    }
    return metal.getName() != null ? metal.getName().trim() : "";
  }

  /**
   * Baut einen für eBay angereicherten Suchbegriff:
   * Falls das Basisterm noch kein Gewicht (oz), keinen Metalltyp und kein "Münze" enthält,
   * werden diese ergänzt.
   */
  private String effectiveEbaySearchTerm(PreciousMetal metal) {
    String base = effectiveSearchTerm(metal);
    String lower = base.toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder(base);

    // Gewicht hinzufügen wenn nicht bereits erwähnt
    if (!lower.contains("oz") && !WEIGHT_G_IN_TEXT.matcher(lower).find()) {
      String weightSuffix = buildWeightSuffix(metal.getWeightInGrams());
      if (weightSuffix != null) {
        sb.append(" ").append(weightSuffix);
      }
    }

    // Metalltyp hinzufügen wenn nicht bereits erwähnt
    if (metal.getType() != null
        && !lower.contains("gold") && !lower.contains("silber")
        && !lower.contains("silver")) {
      String typeName = metal.getType() == PreciousMetalType.GOLD ? "Gold" : "Silber";
      sb.append(" ").append(typeName);
    }

    // "Münze" hinzufügen wenn nicht bereits erwähnt
    if (!lower.contains("münze") && !lower.contains("coin") && !lower.contains("münzen")) {
      sb.append(" Münze");
    }

    return sb.toString().trim();
  }

  /**
   * Berechnet das Oz-Kürzel für ein gegebenes Gewicht oder fällt auf Gramm zurück.
   *
   * @param grams Gewicht in Gramm
   * @return z.B. "1oz", "1/2oz", "31g" oder {@code null} wenn grams &lt;= 0
   */
  static String buildWeightSuffix(double grams) {
    if (grams <= 0) {
      return null;
    }
    for (int i = 0; i < OZ_VALUES.length; i++) {
      double expected = OZ_VALUES[i] * TROY_OZ_GRAMS;
      if (Math.abs(grams - expected) <= OZ_TOLERANCE_GRAMS) {
        return OZ_LABELS[i];
      }
    }
    return Math.round(grams) + "g";
  }

  // ─── Hilfsmethoden ───────────────────────────────────────────────────────

  private double computeSpotValue(PreciousMetal metal, double goldPerOz, double silverPerOz) {
    if (metal == null || metal.getWeightInGrams() <= 0) {
      return 0;
    }
    double pricePerOz = metal.getType() == PreciousMetalType.GOLD ? goldPerOz : silverPerOz;
    return metal.getWeightInGrams() / TROY_OZ_GRAMS * pricePerOz;
  }

  private Map<String, Double> loadPreviousPrices(List<PreciousMetal> metals,
                                                  CollectorCoinPriceSource source) {
    Map<String, Double> prev = new HashMap<>();
    for (PreciousMetal metal : metals) {
      collectorCoinPriceRepository
          .findTopByPreciousMetalIdAndSourceOrderByTimestampDesc(metal.getId(), source)
          .ifPresent(p -> prev.put(metal.getId(), p.getPriceEur()));
    }
    return prev;
  }

  private void saveScraperRun(CollectorCoinPriceSource source, int attempted, int succeeded,
                               List<CollectorScraperRun.Entry> entries) {
    if (scraperRunRepository == null) {
      return;
    }
    try {
      scraperRunRepository.save(CollectorScraperRun.builder()
          .timestamp(Instant.now(clock))
          .source(source)
          .attempted(attempted)
          .succeeded(succeeded)
          .entries(entries)
          .build());
    } catch (Exception e) {
      log.warn("Scraper-Run-Protokoll konnte nicht gespeichert werden: {}", e.getMessage());
    }
  }

  private CollectorCoinPrice buildEntry(PreciousMetal metal,
                                         CollectorCoinPriceSource source,
                                         double priceEur,
                                         String url,
                                         String notes) {
    return CollectorCoinPrice.builder()
        .preciousMetalId(metal.getId())
        .preciousMetalName(metal.getName())
        .source(source)
        .priceEur(priceEur)
        .sourceUrl(url)
        .notes(notes)
        .timestamp(Instant.now(clock))
        .build();
  }

  private OptionalDouble extractLowestPrice(List<ElementHandle> elements) {
    return extractLowestPrice(elements, 2.0);
  }

  private OptionalDouble extractLowestPrice(List<ElementHandle> elements, double minPrice) {
    double lowest = Double.MAX_VALUE;
    boolean found = false;
    for (ElementHandle el : elements) {
      try {
        OptionalDouble p = parseEurValue(el.innerText());
        if (p.isPresent() && p.getAsDouble() >= minPrice && p.getAsDouble() < lowest) {
          lowest = p.getAsDouble();
          found = true;
        }
      } catch (Exception ignored) {
        // Element nicht lesbar, überspringen
      }
    }
    return found ? OptionalDouble.of(lowest) : OptionalDouble.empty();
  }

  /**
   * Wie {@link #extractLowestPrice} – gibt jedoch nur dann einen Preis zurück,
   * wenn mindestens 2 preislich unterschiedliche Einträge gefunden wurden (>2 EUR Abstand).
   * Das verhindert Falsch-Treffer wenn MA-Shops eine generische Kategorie-Seite
   * mit einheitlichem Preis zurückliefert.
   *
   * @param elements  Preis-Elemente
   * @param minPrice  Mindestpreis
   * @return günstigster Preis wenn Preisvarianz vorhanden, sonst leer
   */
  private OptionalDouble extractLowestPriceWithVariance(List<ElementHandle> elements,
                                                         double minPrice) {
    List<Double> allPrices = new ArrayList<>();
    for (ElementHandle el : elements) {
      try {
        OptionalDouble p = parseEurValue(el.innerText());
        if (p.isPresent() && p.getAsDouble() >= minPrice) {
          allPrices.add(p.getAsDouble());
        }
      } catch (Exception ignored) {
        // ignorieren
      }
    }
    if (allPrices.isEmpty()) {
      return OptionalDouble.empty();
    }
    double min = allPrices.stream().mapToDouble(d -> d).min().getAsDouble();
    double max = allPrices.stream().mapToDouble(d -> d).max().getAsDouble();
    // Mindest-Preisvarianz: 2 EUR Unterschied zwischen günstigstem und teuerstem Angebot
    // → sonst ist es wahrscheinlich eine generische Kategorie-Seite
    if (max - min < 2.0) {
      log.debug("MA-Shops: Preise zu homogen ({} – {} EUR) – wahrscheinlich keine echten Treffer",
          String.format("%.2f", min), String.format("%.2f", max));
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(min);
  }

  private OptionalDouble parseFirstEurFromText(String text) {
    if (text == null || text.isBlank()) {
      return OptionalDouble.empty();
    }
    Matcher m = EUR_PRICE.matcher(text);
    while (m.find()) {
      OptionalDouble p = parseEurValue(m.group(1));
      if (p.isPresent() && p.getAsDouble() > 0.5) {
        return p;
      }
    }
    return OptionalDouble.empty();
  }

  /**
   * Parst einen EUR-Preis aus einem String.
   * Unterstützt deutsches Format (1.234,56) und englisches Format (1234.56).
   *
   * @param text Eingabetext
   * @return geparster Wert oder leer
   */
  static OptionalDouble parseEurValue(String text) {
    if (text == null || text.isBlank()) {
      return OptionalDouble.empty();
    }
    String s = text.replaceAll("[^\\d.,]", "").trim();
    if (s.isBlank()) {
      return OptionalDouble.empty();
    }
    if (s.contains(",")) {
      s = s.replace(".", "").replace(",", ".");
    }
    try {
      double v = Double.parseDouble(s);
      return v > 0 ? OptionalDouble.of(v) : OptionalDouble.empty();
    } catch (NumberFormatException e) {
      return OptionalDouble.empty();
    }
  }

  private static String encode(String term) {
    return URLEncoder.encode(term, StandardCharsets.UTF_8);
  }

  private static void closePage(Page page) {
    try {
      page.close();
    } catch (Exception ignored) {
      // ignore
    }
  }

  private static void sleepMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ─── silber-corner.de – HTTP Scraper ──────────────────────────────────────

  /**
   * Regex für Produktname und Preis aus dem silber-corner.de dataLayer JSON.
   * Jedes Produkt: {"name":"...","id":"...","price":"79.2","brand":"..."}
   */
  private static final Pattern SC_PRODUCT = Pattern.compile(
      "\"name\":\"([^\"]+)\",\"id\":\"[^\"]+\",\"price\":\"([0-9.]+)\"");

  /**
   * Scrapt Preise von silber-corner.de für alle Münzen.
   *
   * @param metals Münzliste
   * @return neu gespeicherte Preis-Einträge
   */
  private List<CollectorCoinPrice> updateFromSilberCorner(List<PreciousMetal> metals) {
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    Map<String, Double> prevPrices =
        loadPreviousPrices(metals, CollectorCoinPriceSource.SILBER_CORNER);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String term = effectiveSearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = scrapeSilberCorner(http, metal);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  SILBER_CORNER – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1000);
      } catch (Exception e) {
        log.warn("  silber-corner.de – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
      runEntries.add(CollectorScraperRun.Entry.builder()
          .metalId(metal.getId())
          .metalName(metal.getName())
          .searchTerm(term)
          .success(entry != null)
          .priceEur(entry != null ? entry.getPriceEur() : null)
          .previousPriceEur(prevPrices.get(metal.getId()))
          .build());
    }

    saveScraperRun(CollectorCoinPriceSource.SILBER_CORNER,
        metals.size(), results.size(), runEntries);
    log.info("CollectorCoinPricingService: SILBER_CORNER fertig – {} Einträge gespeichert",
        results.size());
    return results;
  }

  /**
   * Scrapt silber-corner.de für eine einzelne Münze mit Fuzzy-Fallback.
   *
   * @param http  HTTP-Client
   * @param metal Münze
   * @return günstigster passender Preis oder {@code null}
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice scrapeSilberCorner(HttpClient http, PreciousMetal metal)
      throws IOException, InterruptedException {
    List<String> terms = buildFuzzyTerms(effectiveSearchTerm(metal));
    for (int i = 0; i < terms.size(); i++) {
      CollectorCoinPrice result = scrapeSilberCornerWithTerm(http, metal, terms.get(i));
      if (result != null) {
        return result;
      }
      if (i < terms.size() - 1) {
        sleepMs(400);
      }
    }
    return null;
  }

  /**
   * Fetcht und parst eine silber-corner.de Suche für einen einzelnen Suchbegriff.
   * Produktdaten sind als JSON im {@code dataLayer.push} Block eingebettet.
   *
   * @param http       HTTP-Client
   * @param metal      Münze
   * @param searchTerm Suchbegriff
   * @return günstigster passender Preis oder {@code null}
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice scrapeSilberCornerWithTerm(HttpClient http,
                                                         PreciousMetal metal,
                                                         String searchTerm)
      throws IOException, InterruptedException {
    String url = "https://www.silber-corner.de/search?q=" + encode(searchTerm);
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
        .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.5")
        .header("Accept-Encoding", "identity")
        .GET()
        .timeout(Duration.ofSeconds(20))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.debug("silber-corner.de HTTP {} für '{}'", resp.statusCode(), searchTerm);
      return null;
    }

    String typeLabel = (metal.getType() == PreciousMetalType.GOLD) ? "GOLD" : "SILBER";
    Matcher m = SC_PRODUCT.matcher(resp.body());
    Double bestPrice = null;
    String bestTitle = null;
    while (m.find()) {
      String title = m.group(1).toUpperCase(Locale.ROOT);
      if (!title.contains(typeLabel)) {
        continue;
      }
      if (!scTitleMatchesWeight(title, metal.getWeightInGrams())) {
        continue;
      }
      try {
        double price = Double.parseDouble(m.group(2));
        if (price > 0 && (bestPrice == null || price < bestPrice)) {
          bestPrice = price;
          bestTitle = m.group(1);
        }
      } catch (NumberFormatException ignored) {
        // ignorieren
      }
    }

    if (bestPrice == null) {
      log.debug("silber-corner.de: kein Treffer für '{}' (type={})", searchTerm, typeLabel);
      return null;
    }

    return buildEntry(metal, CollectorCoinPriceSource.SILBER_CORNER, bestPrice, url, bestTitle);
  }

  /**
   * Prüft ob ein Produkttitel (uppercase) zum Gewicht der Münze passt.
   * Gibt {@code true} zurück wenn das Gewicht keiner bekannten Oz-Stufe entspricht.
   *
   * @param upperTitle Produkttitel in Großbuchstaben
   * @param grams      Gewicht in Gramm
   * @return {@code true} wenn Titel zur Gewichtsklasse passt
   */
  private boolean scTitleMatchesWeight(String upperTitle, double grams) {
    if (grams <= 0) {
      return true;
    }
    if (Math.abs(grams - TROY_OZ_GRAMS) < 2.0) {
      return upperTitle.contains("1 OZ") || upperTitle.contains("1 UNZE")
          || upperTitle.contains("1OZ");
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 2) < 1.5) {
      return upperTitle.contains("1/2 OZ") || upperTitle.contains("1/2 UNZE")
          || upperTitle.contains("0,5 OZ");
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 4) < 1.0) {
      return upperTitle.contains("1/4 OZ") || upperTitle.contains("1/4 UNZE");
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 10) < 0.5) {
      return upperTitle.contains("1/10 OZ") || upperTitle.contains("1/10 UNZE");
    }
    if (Math.abs(grams - TROY_OZ_GRAMS / 20) < 0.3) {
      return upperTitle.contains("1/20 OZ") || upperTitle.contains("1/20 UNZE");
    }
    // Unbekannte Oz-Stufe – nicht filtern
    return true;
  }

  // ─── silberling.de – Playwright-Stub ──────────────────────────────────────

  /**
   * Playwright-Scraper für silberling.de (noch nicht implementiert).
   * silberling.de ist ein Shopware-5-Shop hinter Cloudflare und liefert
   * für alle URLs außer der Startseite 404, weshalb HTTP-Scraping nicht möglich ist.
   * Gibt eine leere Liste zurück bis ein Playwright-basierter Scraper implementiert wird.
   *
   * @param metals Münzliste (unbenutzt)
   * @return leere Liste
   */
  private List<CollectorCoinPrice> updateFromSilberling(List<PreciousMetal> metals) {
    log.info("SILBERLING: silberling.de benötigt Playwright (Cloudflare/Shopware-5) –"
        + " Scraper noch nicht implementiert, Quelle wird übersprungen.");
    return List.of();
  }

  // ─── gold.de Einzelmünzen-Scraper ─────────────────────────────────────────

  /**
   * Scrapt gold.de für eine einzelne Münze (per-Metal-Variante).
   * Versucht den primären Suchbegriff, dann Fuzzy-Fallbacks.
   *
   * @param http  HTTP-Client
   * @param metal Münze
   * @return Preis-Eintrag oder {@code null}
   * @throws IOException          bei Netzwerkfehler
   * @throws InterruptedException bei Unterbrechung
   */
  private CollectorCoinPrice scrapeSingleGoldDe(HttpClient http, PreciousMetal metal)
      throws IOException, InterruptedException {
    for (String term : buildFuzzyTerms(effectiveSearchTerm(metal))) {
      CollectorCoinPrice result = fetchGoldDePrice(http, metal, term);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  // ─── Fuzzy-Search-Hilfsmethoden ───────────────────────────────────────────

  /**
   * Baut eine Liste von Suchbegriffen für Fuzzy-Fallback:
   * Beginnt mit dem primären Begriff und entfernt schrittweise das letzte Wort.
   * Stoppt wenn der verkürzte Begriff nur noch 2 oder weniger Wörter hätte.
   *
   * <p>Beispiel: "Wiener Philharmoniker 1oz Silber" →
   * ["Wiener Philharmoniker 1oz Silber", "Wiener Philharmoniker 1oz"]</p>
   *
   * @param primary primärer Suchbegriff
   * @return Liste von Suchbegriffen (mindestens 1 Eintrag wenn primary nicht leer)
   */
  List<String> buildFuzzyTerms(String primary) {
    List<String> terms = new ArrayList<>();
    if (primary == null || primary.isBlank()) {
      return terms;
    }
    terms.add(primary.trim());
    String current = primary.trim();
    while (true) {
      int lastSpace = current.lastIndexOf(' ');
      if (lastSpace <= 0) {
        break;
      }
      String shorter = current.substring(0, lastSpace).trim();
      if (shorter.split("\\s+").length <= 2) {
        break;
      }
      terms.add(shorter);
      current = shorter;
    }
    return terms;
  }
}
