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
 * MA-Shops, eBay.de (Browse API, aktive Angebote), Coininvest.de und Numista-API.
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
   *
   * @return alle neu gespeicherten Preis-Einträge
   */
  public List<CollectorCoinPrice> updateFromAllSources() {
    List<CollectorCoinPrice> all = new ArrayList<>();
    for (CollectorCoinPriceSource source : CollectorCoinPriceSource.values()) {
      try {
        all.addAll(updateFromSource(source));
      } catch (Exception e) {
        log.warn("Fehler bei Quelle {}: {}", source, e.getMessage());
      }
    }
    return all;
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
      return updateFromEbaySold(metals);
    }

    // Playwright-basierte Quellen (MA-Shops, Coininvest)
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
      case COININVEST -> scrapeCoininvest(context, metal, term);
      default -> null;
    };
  }

  // MA-Shops ──────────────────────────────────────────────────────────────

  private CollectorCoinPrice scrapeMaShops(BrowserContext context,
                                            PreciousMetal metal,
                                            String term) {
    String url = "https://www.ma-shops.de/shops/search.php?l=de&s=" + encode(term);
    Page page = context.newPage();
    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(20000));
      page.waitForTimeout(2000);

      List<ElementHandle> priceCells = page.querySelectorAll("td.spx-price");
      List<ElementHandle> firstPriceSpans = new ArrayList<>();
      for (ElementHandle cell : priceCells) {
        ElementHandle firstSpan = cell.querySelector("span.price");
        if (firstSpan != null) {
          firstPriceSpans.add(firstSpan);
        }
      }
      OptionalDouble price = extractLowestPrice(firstPriceSpans, 10.0);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot");
      }

      List<ElementHandle> cardSpans = page.querySelectorAll("span.itemPrice span.curr1.price");
      price = extractLowestPrice(cardSpans, 10.0);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot – Galerie");
      }

      log.debug("MA-Shops: keine Treffer für '{}'", term);
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

  /** Cached eBay Marketplace-Insights token (requires buy.marketplace.insights scope). */
  private volatile String ebayInsightsToken = null;

  /** Epoch-second at which the insights token expires. */
  private volatile long ebayInsightsTokenExpiresAt = 0L;

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

  // eBay Marketplace Insights (verkaufte Artikel) ────────────────────────

  private List<CollectorCoinPrice> updateFromEbaySold(List<PreciousMetal> metals) {
    String appId = appConfigService.get(AppConfigService.KEY_EBAY_APP_ID);
    String certId = appConfigService.get(AppConfigService.KEY_EBAY_CERT_ID);
    if (appId.isBlank() || certId.isBlank()) {
      log.warn("eBay App ID oder Cert ID nicht konfiguriert. Überspringe eBay Sold.");
      return List.of();
    }

    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    String token;
    try {
      token = getEbayInsightsToken(http, appId, certId);
    } catch (Exception e) {
      log.warn("eBay Insights Token-Abruf fehlgeschlagen: {}", e.getMessage());
      return List.of();
    }
    if (token == null || token.isBlank()) {
      log.warn("eBay Sold: leeres Insights-Token – überspringe Batch");
      return List.of();
    }

    Map<String, Double> prevPrices =
        loadPreviousPrices(metals, CollectorCoinPriceSource.EBAY_SOLD);
    List<CollectorCoinPrice> results = new ArrayList<>();
    List<CollectorScraperRun.Entry> runEntries = new ArrayList<>();

    for (PreciousMetal metal : metals) {
      String term = effectiveEbaySearchTerm(metal);
      CollectorCoinPrice entry = null;
      try {
        entry = fetchEbaySoldPrice(http, metal, term, token);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  EBAY_SOLD – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(1000);
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
   * Returns a valid eBay Marketplace-Insights OAuth2 token.
   * Requests the {@code buy.marketplace.insights} scope in addition to the base scope.
   *
   * @param http   the HTTP client
   * @param appId  eBay App ID (Client ID)
   * @param certId eBay Cert ID (Client Secret)
   * @return access token string, or {@code null} on failure
   * @throws IOException          on network error
   * @throws InterruptedException if interrupted
   */
  private String getEbayInsightsToken(HttpClient http, String appId, String certId)
      throws IOException, InterruptedException {
    long nowSec = Instant.now(clock).getEpochSecond();
    if (ebayInsightsToken != null && nowSec < ebayInsightsTokenExpiresAt - 60) {
      return ebayInsightsToken;
    }

    String credentials = Base64.getEncoder().encodeToString(
        (appId + ":" + certId).getBytes(StandardCharsets.UTF_8));
    String scope = "https://api.ebay.com/oauth/api_scope"
        + " https://api.ebay.com/oauth/api_scope/buy.marketplace.insights";
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://api.ebay.com/identity/v1/oauth2/token"))
        .header("Authorization", "Basic " + credentials)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(
            "grant_type=client_credentials&scope=" + encode(scope)))
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.warn("eBay Insights Token HTTP {}: {}", resp.statusCode(),
          resp.body().substring(0, Math.min(300, resp.body().length())));
      return null;
    }

    Matcher tokenMatcher = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
        .matcher(resp.body());
    if (!tokenMatcher.find()) {
      log.warn("eBay Insights Token-Antwort enthält kein access_token");
      return null;
    }
    String token = tokenMatcher.group(1);

    Matcher expMatcher = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)").matcher(resp.body());
    long expiresIn = expMatcher.find() ? Long.parseLong(expMatcher.group(1)) : 7200L;

    ebayInsightsToken = token;
    ebayInsightsTokenExpiresAt = nowSec + expiresIn;
    log.info("eBay Insights-Token abgerufen, gültig für {}s", expiresIn);
    return token;
  }

  /**
   * Fetches the average price of the top-5 recently sold eBay listings
   * via the Marketplace Insights API.
   *
   * @param http  the HTTP client
   * @param metal the coin to price
   * @param term  the (enriched) search term
   * @param token the OAuth2 Bearer token with insights scope
   * @return a {@link CollectorCoinPrice} entry, or {@code null} if no usable result
   * @throws IOException          on network error
   * @throws InterruptedException if interrupted
   */
  private CollectorCoinPrice fetchEbaySoldPrice(HttpClient http,
                                                 PreciousMetal metal,
                                                 String term,
                                                 String token)
      throws IOException, InterruptedException {
    String searchUrl =
        "https://api.ebay.com/buy/marketplace_insights/v1_beta/item_sales/search"
            + "?q=" + encode(term)
            + "&category_ids=11116"
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
    if (resp.statusCode() == 403 || resp.statusCode() == 404) {
      log.warn("eBay Sold API HTTP {} für '{}' – Scope oder Endpunkt nicht verfügbar",
          resp.statusCode(), term);
      return null;
    }
    if (resp.statusCode() != 200) {
      log.warn("eBay Sold API HTTP {} für '{}': {}", resp.statusCode(), term,
          resp.body().substring(0, Math.min(300, resp.body().length())));
      return null;
    }

    // Extract: "lastSoldPrice":{"value":"45.00","currency":"EUR"}
    Matcher m = Pattern.compile(
        "\"lastSoldPrice\"\\s*:\\s*\\{[^}]*?\"value\"\\s*:\\s*\"([\\d.]+)\"")
        .matcher(resp.body());
    List<Double> prices = new ArrayList<>();
    while (m.find()) {
      try {
        double p = Double.parseDouble(m.group(1));
        if (p > 1.0) {
          prices.add(p);
        }
      } catch (NumberFormatException ignored) {
        // ignored
      }
    }

    if (prices.isEmpty()) {
      log.debug("eBay Sold: keine Verkaufspreise für '{}'", term);
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
        "Ø " + count + " verkaufte Angebote (Insights API)");
  }

  // Coininvest.de ────────────────────────────────────────────────────────

  private CollectorCoinPrice scrapeCoininvest(BrowserContext context,
                                               PreciousMetal metal,
                                               String term) {
    String url = "https://stonexbullion.com/de/search/?term=" + encode(term);
    Page page = context.newPage();
    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(25000));
      page.waitForTimeout(4000);

      String[] selectors = {
          ".product-price",
          ".price-wrapper .price",
          "[data-price-amount]",
          ".price-box .price",
          ".product-item-price .price",
          "span.price"
      };

      for (String sel : selectors) {
        List<ElementHandle> els = page.querySelectorAll(sel);
        OptionalDouble price = extractLowestPrice(els);
        if (price.isPresent()) {
          return buildEntry(metal, CollectorCoinPriceSource.COININVEST,
              price.getAsDouble(), url, "günstigstes Angebot");
        }
      }

      OptionalDouble price = parseFirstEurFromText(page.innerText("body"));
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.COININVEST,
            price.getAsDouble(), url, "Textextraktion");
      }
      return null;
    } finally {
      closePage(page);
    }
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
}
