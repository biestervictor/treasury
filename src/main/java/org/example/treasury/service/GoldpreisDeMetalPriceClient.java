package org.example.treasury.service;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holt Gold-/Silberpreise (EUR pro Unze) von goldpreis.de via jsoup.
 *
 * <p>Quellen:</p>
 * <ul>
 *   <li>Gold: https://www.goldpreis.de</li>
 *   <li>Silber: https://www.goldpreis.de/silberpreis/</li>
 * </ul>
 *
 * <p>Hinweis: HTML-Scraping ist naturgemäß fragil. Die Extraktion ist daher defensiv implementiert.
 * Bei Fehlern wird eine Exception geworfen (und im Service wird dann nichts persistiert).</p>
 */
@Component
public class GoldpreisDeMetalPriceClient implements MetalPriceClient {

  private static final Logger log = LoggerFactory.getLogger(GoldpreisDeMetalPriceClient.class);

  private static final String GOLD_URL = "https://www.goldpreis.de";
  private static final String SILVER_URL = "https://www.goldpreis.de/silberpreis/";

  // Fallback Regex: findet z.B. "3.864,38" oder "3864,38" (EUR/oz)
  private static final Pattern EURO_NUMBER = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*|\\d+),(\\d{2})");

  @Override
  public Prices fetchCurrentPrices() {
    double gold = fetchEurPerOunce(GOLD_URL, "gold");
    double silver = fetchEurPerOunce(SILVER_URL, "silber");
    return new Prices(gold, silver, Instant.now());
  }

  private double fetchEurPerOunce(String url, String label) {
    try {
      Document doc = Jsoup.connect(url)
          .userAgent("Mozilla/5.0 (compatible; treasury-bot/1.0)")
          .timeout(15000)
          .get();

      // 1) Versuche strukturierte Daten / Meta zu finden
      String text = doc.text();
      Double parsed = tryParseFromText(text);
      if (parsed != null) {
        return parsed;
      }

      // 2) Fallback: auch aus HTML (inkl. Script) suchen
      String html = doc.outerHtml();
      parsed = tryParseFromText(html);
      if (parsed != null) {
        return parsed;
      }

      throw new IllegalStateException("Konnte " + label + " Preis nicht aus HTML extrahieren (" + url + ")");
    } catch (Exception e) {
      log.warn("Preis-Scrape fehlgeschlagen ({}): {}", label, e.getMessage());
      throw new IllegalStateException("Preis-Scrape fehlgeschlagen (" + label + ")", e);
    }
  }

  private Double tryParseFromText(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }

    Matcher m = EURO_NUMBER.matcher(text);
    if (!m.find()) {
      return null;
    }

    String integerPart = m.group(1).replace(".", "");
    String decimalPart = m.group(2);
    return Double.parseDouble(integerPart + "." + decimalPart);
  }
}

