package org.example.treasury.dto;

import java.util.List;

/**
 * DTO für das globale Asset-Dashboard.
 * Aggregiert alle Asset-Kategorien (Displays, SecretLair, Edelmetalle, Schuhe).
 *
 * @param categories           Übersicht pro Asset-Kategorie
 * @param totalInvested        Gesamt-Einkaufswert über alle Kategorien
 * @param totalCurrentValue    Aktueller Gesamtmarktwert
 * @param totalProfit          Gesamtgewinn (currentValue - invested)
 * @param totalBelowPurchase   Anzahl Positionen unter Einkaufspreis
 * @param topGainers           Top-5 Gewinner (höchster absoluter Gewinn)
 * @param topLosers            Top-5 Verlierer (höchster absoluter Verlust)
 * @param belowPurchaseItems   Alle Positionen mit aktuellem Wert unter Einkaufspreis
 */
public record DashboardDto(
    List<CategorySummary> categories,
    double totalInvested,
    double totalCurrentValue,
    double totalProfit,
    int totalBelowPurchase,
    List<ItemHighlight> topGainers,
    List<ItemHighlight> topLosers,
    List<ItemHighlight> belowPurchaseItems
) {

  /**
   * Zusammenfassung einer Asset-Kategorie.
   *
   * @param name                  Anzeigename der Kategorie
   * @param colorClass            Bootstrap-Farb-CSS-Klasse (z.B. "primary", "success")
   * @param count                 Anzahl aktiver (nicht verkaufter) Positionen
   * @param totalInvested         Summe der Einkaufspreise
   * @param currentValue          Aktueller Gesamtmarktwert
   * @param profit                Gewinn (currentValue - totalInvested)
   * @param belowPurchasePriceCount Anzahl Positionen unter Einkaufspreis
   * @param linkUrl               Link zur Detail-Ansicht
   */
  public record CategorySummary(
      String name,
      String colorClass,
      int count,
      double totalInvested,
      double currentValue,
      double profit,
      int belowPurchasePriceCount,
      String linkUrl
  ) {
  }

  /**
   * Einzelne Position für Top-Gewinner / Top-Verlierer / Positionen unter Einkaufspreis.
   *
   * @param category     Name der Asset-Kategorie
   * @param name         Bezeichnung der Position
   * @param invested     Einkaufspreis
   * @param currentValue Aktueller Marktwert
   * @param profit       Gewinn (negativ = Verlust)
   * @param linkUrl      Link zur zugehörigen Detail-Ansicht
   */
  public record ItemHighlight(
      String category,
      String name,
      double invested,
      double currentValue,
      double profit,
      String linkUrl
  ) {
  }
}
