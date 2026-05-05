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
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.treasury.model.CollectorCoinPrice;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.repository.CollectorCoinPriceRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestriert die Preisermittlung für Sammlermünzen aus vier externen Quellen:
 * MA-Shops, eBay.de (abgeschlossene Verkäufe), Coininvest.de und Numista-API.
 *
 * <p>Jede Quelle kann separat oder gemeinsam getriggert werden.
 * Die ermittelten Preise werden in {@code collectorCoinPrice} gespeichert
 * und dienen als Grundlage für den Preisverlauf-Chart je Münze.</p>
 */
@Service
public class CollectorCoinPricingService {

  private static final Logger log = LoggerFactory.getLogger(CollectorCoinPricingService.class);

  /** Regex für EUR-Preise im Format "45,00" / "1.234,56" / "45.00" / "1234.56". */
  private static final Pattern EUR_PRICE = Pattern.compile(
      "(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})");

  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

  private final PreciousMetalRepository preciousMetalRepository;
  private final CollectorCoinPriceRepository collectorCoinPriceRepository;
  private final Clock clock;
  private final AppConfigService appConfigService;

  /**
   * Konstruktor.
   *
   * @param preciousMetalRepository      Münz-Repository
   * @param collectorCoinPriceRepository Preisverlauf-Repository
   * @param appConfigService             App-Konfigurationsservice (für Numista-API-Key)
   */
  @Autowired
  public CollectorCoinPricingService(PreciousMetalRepository preciousMetalRepository,
                                     CollectorCoinPriceRepository collectorCoinPriceRepository,
                                     AppConfigService appConfigService) {
    this(preciousMetalRepository, collectorCoinPriceRepository, appConfigService, Clock.systemUTC());
  }

  CollectorCoinPricingService(PreciousMetalRepository preciousMetalRepository,
                               CollectorCoinPriceRepository collectorCoinPriceRepository,
                               AppConfigService appConfigService,
                               Clock clock) {
    this.preciousMetalRepository = preciousMetalRepository;
    this.collectorCoinPriceRepository = collectorCoinPriceRepository;
    this.appConfigService = appConfigService;
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

    List<CollectorCoinPrice> results = new ArrayList<>();
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions()
              .setHeadless(true)
              // Pflicht-Args für Chromium in Docker/Kubernetes (kein /dev/shm)
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
          try {
            CollectorCoinPrice entry = scrapeWithPlaywright(source, context, metal, term);
            if (entry != null) {
              results.add(collectorCoinPriceRepository.save(entry));
              log.info("  {} – {}: {} EUR", source, metal.getName(),
                  String.format("%.2f", entry.getPriceEur()));
            }
          } catch (Exception e) {
            log.warn("  {} – {} Scrape fehlgeschlagen: {}", source, metal.getName(), e.getMessage());
          }
          sleepMs(1500);
        }
      } finally {
        browser.close();
      }
    }
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
    // Korrekte Such-URL für ma-shops.de (die /search/?q= Variante liefert 404)
    String url = "https://www.ma-shops.de/shops/search.php?l=de&s=" + encode(term);
    Page page = context.newPage();
    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(20000));
      page.waitForTimeout(2000);

      // Tabellen-Ansicht: direktes Kind von td.spx-price (verhindert Versandkosten-Spans)
      List<ElementHandle> tableSpans = page.querySelectorAll("td.spx-price > span.price");
      OptionalDouble price = extractLowestPrice(tableSpans);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot");
      }

      // Karten-Ansicht: span.curr1.price (Featured-Bereich oben auf der Suchergebnisseite)
      List<ElementHandle> cardSpans = page.querySelectorAll("span.curr1.price");
      price = extractLowestPrice(cardSpans);
      if (price.isPresent()) {
        return buildEntry(metal, CollectorCoinPriceSource.MA_SHOPS,
            price.getAsDouble(), url, "günstigstes Angebot");
      }

      log.debug("MA-Shops: keine Treffer für '{}'", term);
      return null;
    } finally {
      closePage(page);
    }
  }

  // eBay Finding API ─────────────────────────────────────────────────────

  private List<CollectorCoinPrice> updateFromEbay(List<PreciousMetal> metals) {
    String appId = appConfigService.get(AppConfigService.KEY_EBAY_APP_ID);
    if (appId.isBlank()) {
      log.warn("eBay App ID nicht konfiguriert (Settings → API-Keys). Überspringe eBay.");
      return List.of();
    }

    List<CollectorCoinPrice> results = new ArrayList<>();
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    for (PreciousMetal metal : metals) {
      String term = effectiveSearchTerm(metal);
      try {
        CollectorCoinPrice entry = fetchEbayPrice(http, metal, term, appId);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  EBAY – {}: {} EUR", metal.getName(),
              String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(500);
      } catch (Exception e) {
        log.warn("  EBAY – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
    }
    log.info("CollectorCoinPricingService: EBAY fertig – {} Einträge gespeichert", results.size());
    return results;
  }

  private CollectorCoinPrice fetchEbayPrice(HttpClient http,
                                             PreciousMetal metal,
                                             String term,
                                             String appId)
      throws IOException, InterruptedException {
    String apiUrl = "https://svcs.ebay.com/services/search/FindingService/v1"
        + "?OPERATION-NAME=findCompletedItems"
        + "&SERVICE-VERSION=1.0.0"
        + "&SECURITY-APPNAME=" + encode(appId)
        + "&RESPONSE-DATA-FORMAT=JSON"
        + "&REST-PAYLOAD"
        + "&keywords=" + encode(term)
        + "&itemFilter%280%29.name=SoldItemsOnly"
        + "&itemFilter%280%29.value=true"
        + "&categoryId=11116"
        + "&paginationInput.entriesPerPage=10"
        + "&sortOrder=EndTimeSoonest";

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(apiUrl))
        .header("User-Agent", "treasury-bot/1.0")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      log.warn("eBay Finding API HTTP {}: {}", resp.statusCode(), term);
      return null;
    }

    // Preise aus JSON extrahieren: "convertedCurrentPrice":[{"__value__":"45.00","@currencyId":"EUR"}]
    Pattern pricePattern = Pattern.compile(
        "\"convertedCurrentPrice\":\\[\\{\"__value__\":\"([\\d.]+)\",\"@currencyId\":\"EUR\"\\}\\]");
    List<Double> prices = new ArrayList<>();
    Matcher m = pricePattern.matcher(resp.body());
    while (m.find()) {
      try {
        double price = Double.parseDouble(m.group(1));
        if (price > 0) {
          prices.add(price);
        }
      } catch (NumberFormatException ignored) {
        // ignored
      }
    }

    if (prices.isEmpty()) {
      log.debug("eBay: keine abgeschlossenen Verkäufe für '{}'", term);
      return null;
    }

    int count = Math.min(5, prices.size());
    double avg = prices.stream().limit(count).mapToDouble(d -> d).average().orElse(0);
    if (avg <= 0) {
      return null;
    }

    String sourceUrl = "https://www.ebay.de/sch/i.html?_nkw=" + encode(term)
        + "&LH_Sold=1&LH_Complete=1&_sacat=11116";
    return buildEntry(metal, CollectorCoinPriceSource.EBAY, avg, sourceUrl,
        "Ø " + count + " abgeschlossene Verkäufe (Finding API)");
  }

  // Coininvest.de ────────────────────────────────────────────────────────

  private CollectorCoinPrice scrapeCoininvest(BrowserContext context,
                                               PreciousMetal metal,
                                               String term) {
    String url = "https://www.coininvest.de/search/?q=" + encode(term);
    Page page = context.newPage();
    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(20000));
      page.waitForTimeout(2000);

      String[] selectors = {
          ".price-box .price",
          ".product-item-price .price",
          ".buy-price",
          ".product-price",
          "[class*='price']"
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
      log.warn("Numista-API-Key nicht konfiguriert (Settings → API-Keys). Überspringe Numista.");
      return List.of();
    }

    List<CollectorCoinPrice> results = new ArrayList<>();
    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    for (PreciousMetal metal : metals) {
      String term = effectiveSearchTerm(metal);
      try {
        CollectorCoinPrice entry = fetchNumistaPrice(http, metal, term, numistaApiKey);
        if (entry != null) {
          results.add(collectorCoinPriceRepository.save(entry));
          log.info("  NUMISTA – {}: {} EUR", metal.getName(),
                  String.format("%.2f", entry.getPriceEur()));
        }
        sleepMs(500);
      } catch (Exception e) {
        log.warn("  NUMISTA – {} fehlgeschlagen: {}", metal.getName(), e.getMessage());
      }
    }
    return results;
  }

  private CollectorCoinPrice fetchNumistaPrice(HttpClient http,
                                                PreciousMetal metal,
                                                String term,
                                                String numistaApiKey)
      throws IOException, InterruptedException {
    // 1) Münze suchen
    String searchUrl = "https://api.numista.com/api/v3/find_coins?q="
        + encode(term) + "&lang=de&page=0&rows=1";

    HttpRequest searchReq = HttpRequest.newBuilder()
        .uri(URI.create(searchUrl))
        .header("Numista-API-Key", numistaApiKey)
        .header("User-Agent", "treasury-bot/1.0")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> searchResp = http.send(searchReq, HttpResponse.BodyHandlers.ofString());
    if (searchResp.statusCode() != 200) {
      log.warn("Numista Suche HTTP {}: {}", searchResp.statusCode(), term);
      return null;
    }

    String body = searchResp.body();
    // Einfaches JSON-Parsen ohne Bibliothek: extrahiere "numista_ref"
    Pattern refPattern = Pattern.compile("\"numista_ref\"\\s*:\\s*(\\d+)");
    Matcher refMatcher = refPattern.matcher(body);
    if (!refMatcher.find()) {
      log.debug("Numista: keine Münze für '{}'", term);
      return null;
    }
    String ref = refMatcher.group(1);

    // 2) Preise für diese Münze abrufen
    String priceUrl = "https://api.numista.com/api/v3/coins/" + ref + "/prices";
    HttpRequest priceReq = HttpRequest.newBuilder()
        .uri(URI.create(priceUrl))
        .header("Numista-API-Key", numistaApiKey)
        .header("User-Agent", "treasury-bot/1.0")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build();

    HttpResponse<String> priceResp = http.send(priceReq, HttpResponse.BodyHandlers.ofString());
    if (priceResp.statusCode() != 200) {
      log.debug("Numista Preise HTTP {}: ref={}", priceResp.statusCode(), ref);
      return null;
    }

    // "average_price":{"value":45.0,"currency":"EUR"}
    Pattern avgPattern = Pattern.compile(
        "\"average_price\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*([\\d.]+)[^}]*\"currency\"\\s*:\\s*\"EUR\"");
    Matcher avgMatcher = avgPattern.matcher(priceResp.body());
    if (avgMatcher.find()) {
      double price = Double.parseDouble(avgMatcher.group(1));
      return buildEntry(metal, CollectorCoinPriceSource.NUMISTA, price,
          "https://numista.com/catalogue/pieces/" + ref + ".html",
          "Numista Durchschnittspreis (ref=" + ref + ")");
    }

    // Fallback: erster "value" in EUR
    Pattern valPattern = Pattern.compile(
        "\"value\"\\s*:\\s*([\\d.]+)[^}]*\"currency\"\\s*:\\s*\"EUR\"");
    Matcher valMatcher = valPattern.matcher(priceResp.body());
    if (valMatcher.find()) {
      double price = Double.parseDouble(valMatcher.group(1));
      return buildEntry(metal, CollectorCoinPriceSource.NUMISTA, price,
          "https://numista.com/catalogue/pieces/" + ref + ".html",
          "Numista Preis (ref=" + ref + ")");
    }

    return null;
  }

  // ─── Hilfsmethoden ───────────────────────────────────────────────────────

  private String effectiveSearchTerm(PreciousMetal metal) {
    String term = metal.getCollectorSearchTerm();
    if (term != null && !term.isBlank()) {
      return term.trim();
    }
    return metal.getName() != null ? metal.getName().trim() : "";
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
    double lowest = Double.MAX_VALUE;
    boolean found = false;
    for (ElementHandle el : elements) {
      try {
        OptionalDouble p = parseEurValue(el.innerText());
        if (p.isPresent() && p.getAsDouble() < lowest) {
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
    // Deutsches Format: letztes Komma ist Dezimaltrennzeichen, Punkte sind Tausender
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
