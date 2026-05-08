package org.example.treasury.model;

/**
 * Quellen für Sammlermünz-Marktpreise.
 */
public enum CollectorCoinPriceSource {

  /** gold-silber-anlage.com – Shopify-Shop für kolorierte Sammlermünzen, HTTP-Scraper. */
  MA_SHOPS("gold-silber-anlage.com"),

  /** eBay.de – aktive Angebote via Browse API. */
  EBAY("eBay"),

  /** eBay.de – tatsächlich verkaufte Artikel via Marketplace Insights API. */
  EBAY_SOLD("eBay Verkauft"),

  /** gold.de – Preisvergleich für Edelmetallmünzen und -barren (ehem. Coininvest). */
  COININVEST("gold.de"),

  /** Numista-API – crowdsourced Münz-Datenbank mit Marktpreisen. */
  NUMISTA("Numista"),

  /** silber-corner.de – Edelmetallhändler, HTTP-Scraper via dataLayer JSON. */
  SILBER_CORNER("silber-corner.de"),

  /** silberling.de – Edelmetallhändler, HTTP-Scraper via Kategorie-Seiten (ga4_view_item_list). */
  SILBERLING("silberling.de"),

  /** auragentum.de – Edelmetallhändler (Shopware), HTTP-Scraper via Such-URL (sSearch). */
  AURAGENTUM("auragentum.de"),

  /** emk.com – Edelmetallhändler (Sana Commerce React SPA), Playwright-Scraper. */
  EMK("emk.com");

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

