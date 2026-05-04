package org.example.treasury.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.Optional;
import org.example.treasury.model.Shoe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scrapt Lowest Ask, Highest Bid und Last Sale von StockX für einen Schuh
 * in einer bestimmten US-Größe, indem ein echter Playwright-Chromium-Browser
 * die Produktseite lädt und die eingebetteten {@code __NEXT_DATA__}-JSON-Daten
 * sowie DOM-Elemente als Fallback auswertet.
 *
 * <p>Kein API-Key nötig. Benötigt einen gesetzten {@code stockxSlug} am Schuh-Objekt.
 */
@Service
public class StockxPriceCollectorService {

  private static final Logger logger =
      LoggerFactory.getLogger(StockxPriceCollectorService.class);

  private static final String STOCKX_BASE = "https://stockx.com/";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Holds StockX market data for a shoe/size combination.
   *
   * @param lowestAsk  lowest ask price in EUR (0 if unavailable)
   * @param highestBid highest bid price in EUR (0 if unavailable)
   * @param lastSale   last sale price in EUR (0 if unavailable)
   */
  public record StockxPriceData(double lowestAsk, double highestBid, double lastSale) {
  }

  /**
   * Scrapt StockX-Preisdaten für den angegebenen Schuh via Playwright.
   * Gibt empty zurück wenn kein stockxSlug gesetzt ist oder die Seite nicht ausgewertet
   * werden kann.
   *
   * @param shoe der Schuh
   * @return StockxPriceData oder empty
   */
  public Optional<StockxPriceData> fetchPrices(Shoe shoe) {
    String slug = shoe.getStockxSlug();
    if (slug == null || slug.isBlank()) {
      logger.debug("Kein StockX-Slug für Schuh {}", shoe.getId());
      return Optional.empty();
    }

    String usSize = normalizeUsSize(shoe.getUsSize());
    String url = STOCKX_BASE + slug + (usSize != null ? "?size=" + usSize : "");

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions().setHeadless(true));

      BrowserContext context = browser.newContext(
          new Browser.NewContextOptions()
              .setUserAgent(USER_AGENT)
              .setViewportSize(1280, 800)
              .setLocale("de-DE")
              .setTimezoneId("Europe/Berlin"));

      // Acceptiere Cookies automatisch via extra HTTP headers
      context.setExtraHTTPHeaders(java.util.Map.of(
          "Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
          "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
      ));

      Page page = context.newPage();
      try {
        logger.info("StockX: lade {} ...", url);
        page.navigate(url, new Page.NavigateOptions().setTimeout(30000));

        // Warte bis Seite grundlegend geladen – StockX braucht JS-Rendering
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
            new Page.WaitForLoadStateOptions().setTimeout(15000));

        String pageUrl = page.url();
        logger.debug("StockX: final URL = {}", pageUrl);

        // Versuch 1: __NEXT_DATA__ aus script-Tag extrahieren
        Optional<StockxPriceData> fromNextData = extractFromNextData(page, usSize, slug);
        if (fromNextData.isPresent()) {
          return fromNextData;
        }

        // Versuch 2: DOM-Elemente (gerenderte Preise)
        Optional<StockxPriceData> fromDom = extractFromDom(page);
        if (fromDom.isPresent()) {
          return fromDom;
        }

        logger.warn("StockX: Keine Preisdaten gefunden für slug={}, size={}", slug, usSize);
        // Debug: ersten 500 Zeichen des HTML loggen
        String content = page.content();
        logger.debug("StockX Seiteninhalt (Anfang): {}",
            content.substring(0, Math.min(500, content.length())));
        return Optional.empty();

      } finally {
        page.close();
        browser.close();
      }
    } catch (Exception e) {
      logger.error("StockX-Playwright-Fehler für slug={}: {}", slug, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Extrahiert Preisdaten aus dem eingebetteten {@code __NEXT_DATA__} JSON-Script-Tag.
   * Versucht mehrere bekannte Pfade innerhalb der JSON-Struktur.
   *
   * @param page   die geladene Playwright-Seite
   * @param usSize die normalisierte US-Größe (z.B. "11", "10.5")
   * @param slug   der StockX-Slug (für Logging)
   * @return StockxPriceData oder empty
   */
  private Optional<StockxPriceData> extractFromNextData(Page page, String usSize, String slug) {
    try {
      String nextDataJson = (String) page.evaluate(
          "() => { "
              + "  const el = document.getElementById('__NEXT_DATA__'); "
              + "  return el ? el.textContent : null; "
              + "}"
      );

      if (nextDataJson == null || nextDataJson.isBlank()) {
        logger.debug("StockX: kein __NEXT_DATA__ auf Seite für slug={}", slug);
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(nextDataJson);

      // Pfad A: pageProps.product.variants[]
      JsonNode variants = root.at("/props/pageProps/product/variants");
      if (variants.isArray() && variants.size() > 0) {
        return extractFromVariants(variants, usSize);
      }

      // Pfad B: pageProps.serverRenderData.product.variants[]
      variants = root.at("/props/pageProps/serverRenderData/product/variants");
      if (variants.isArray() && variants.size() > 0) {
        return extractFromVariants(variants, usSize);
      }

      // Pfad C: Gesamtmarktwert ohne Größenfilter (letzter Fallback)
      JsonNode market = root.at("/props/pageProps/product/market");
      if (!market.isMissingNode()) {
        double ask = market.path("lowestAsk").asDouble(0);
        double bid = market.path("highestBid").asDouble(0);
        double last = market.path("lastSale").asDouble(0);
        if (ask > 0 || bid > 0) {
          logger.info("StockX (Pfad C – kein Größenfilter): Ask={} Bid={} Last={}", ask, bid, last);
          return Optional.of(new StockxPriceData(ask, bid, last));
        }
      }

      logger.debug("StockX: __NEXT_DATA__ vorhanden aber kein passender Preispfad für slug={}",
          slug);
    } catch (Exception e) {
      logger.warn("StockX: Fehler beim Parsen von __NEXT_DATA__: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Durchsucht das Variants-Array nach der passenden US-Größe und gibt deren Marktdaten zurück.
   * Gibt alle Größen zurück falls keine passende Größe gefunden wurde.
   *
   * @param variants das JSON-Array mit Varianten
   * @param usSize   gewünschte US-Größe (kann null sein)
   * @return StockxPriceData oder empty
   */
  private Optional<StockxPriceData> extractFromVariants(JsonNode variants, String usSize) {
    JsonNode bestMatch = null;

    for (JsonNode variant : variants) {
      // Größenfeld: "shoeSize", "size", "sizeDescriptor"
      String varSize = variant.path("shoeSize").asText(
          variant.path("size").asText(
              variant.path("sizeDescriptor").asText("")));

      // Normalisierung: "11.0" -> "11"
      if (varSize.endsWith(".0")) {
        varSize = varSize.substring(0, varSize.length() - 2);
      }

      if (usSize != null && usSize.equals(varSize)) {
        bestMatch = variant;
        break;
      }
      // Fallback: erste Variante merken
      if (bestMatch == null) {
        bestMatch = variant;
      }
    }

    if (bestMatch == null) {
      return Optional.empty();
    }

    JsonNode market = bestMatch.path("market");
    double ask = market.path("lowestAsk").asDouble(0);
    double bid = market.path("highestBid").asDouble(0);
    double last = market.path("lastSale").asDouble(0);

    if (ask > 0 || bid > 0 || last > 0) {
      logger.info("StockX (Varianten): Ask={} Bid={} LastSale={} (Größe={})", ask, bid, last,
          usSize);
      return Optional.of(new StockxPriceData(ask, bid, last));
    }
    return Optional.empty();
  }

  /**
   * Extrahiert Preisdaten aus gerenderten DOM-Elementen als Fallback,
   * wenn __NEXT_DATA__ nicht verfügbar oder leer ist.
   *
   * @param page die geladene Playwright-Seite
   * @return StockxPriceData oder empty
   */
  private Optional<StockxPriceData> extractFromDom(Page page) {
    try {
      // StockX rendert Preise in verschiedenen data-testid Attributen
      double ask = parseDomPrice(page,
          "[data-testid='lowest-ask'] p",
          "[data-testid='buy-bar-cta'] p",
          ".lowest-ask");

      double bid = parseDomPrice(page,
          "[data-testid='highest-bid'] p",
          "[data-testid='sell-bar-cta'] p",
          ".highest-bid");

      double last = parseDomPrice(page,
          "[data-testid='last-sale'] p",
          ".last-sale-price");

      if (ask > 0 || bid > 0) {
        logger.info("StockX (DOM): Ask={} Bid={} LastSale={}", ask, bid, last);
        return Optional.of(new StockxPriceData(ask, bid, last));
      }
    } catch (Exception e) {
      logger.debug("StockX DOM-Extraktion fehlgeschlagen: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Versucht nacheinander mehrere CSS-Selektoren und gibt den ersten geparsten Preis zurück.
   *
   * @param page      die geladene Playwright-Seite
   * @param selectors CSS-Selektoren (werden der Reihe nach versucht)
   * @return geparster Preis oder 0.0 wenn keiner gefunden
   */
  private double parseDomPrice(Page page, String... selectors) {
    for (String selector : selectors) {
      try {
        var el = page.querySelector(selector);
        if (el != null) {
          String text = el.innerText().trim()
              .replace("€", "").replace("$", "")
              .replace(".", "").replace(",", ".")
              .trim();
          double val = Double.parseDouble(text);
          if (val > 0) {
            return val;
          }
        }
      } catch (Exception ignored) {
        // nächsten Selektor versuchen
      }
    }
    return 0.0;
  }

  /**
   * Normalisiert die US-Größe (entfernt "US "-Prefix, validiert numerisches Format).
   *
   * @param usSize Rohwert aus der DB
   * @return normalisierte Größe oder null
   */
  private String normalizeUsSize(String usSize) {
    if (usSize == null || usSize.isBlank()) {
      return null;
    }
    String s = usSize.trim();
    if (s.toUpperCase().startsWith("US ")) {
      s = s.substring(3).trim();
    }
    return s.matches("\\d+(\\.\\d+)?") ? s : null;
  }
}
