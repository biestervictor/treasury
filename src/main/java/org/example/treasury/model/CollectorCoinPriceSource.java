package org.example.treasury.model;

/**
 * Quellen für Sammlermünz-Marktpreise.
 */
public enum CollectorCoinPriceSource {

  /** MA-Shops.de – deutschsprachige Münzbörse, kein Cloudflare, Playwright-Scraper. */
  MA_SHOPS("MA-Shops"),

  /** eBay.de – abgeschlossene Auktionen/Sofortkauf-Verkäufe (echte Transaktionspreise). */
  EBAY("eBay"),

  /** Coininvest.de – Händlerpreise für Anlagemünzen und Sammlermünzen. */
  COININVEST("Coininvest"),

  /** Numista-API – crowdsourced Münz-Datenbank mit Marktpreisen. */
  NUMISTA("Numista");

  private final String displayName;

  CollectorCoinPriceSource(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Lesbarer Anzeigename der Quelle.
   *
   * @return Anzeigename
   */
  public String getDisplayName() {
    return displayName;
  }
}
