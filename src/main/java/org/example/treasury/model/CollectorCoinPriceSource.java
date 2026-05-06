package org.example.treasury.model;

/**
 * Quellen für Sammlermünz-Marktpreise.
 */
public enum CollectorCoinPriceSource {

  /** MA-Shops.de – deutschsprachige Münzbörse, kein Cloudflare, Playwright-Scraper. */
  MA_SHOPS("MA-Shops"),

  /** eBay.de – aktive Angebote via Browse API. */
  EBAY("eBay"),

  /** eBay.de – tatsächlich verkaufte Artikel via Marketplace Insights API. */
  EBAY_SOLD("eBay Verkauft"),

  /** gold.de – Preisvergleich für Edelmetallmünzen und -barren (ehem. Coininvest). */
  COININVEST("gold.de"),

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
