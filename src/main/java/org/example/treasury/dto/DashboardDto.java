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
 * @param topGainers           Top Gewinner (höchster absoluter Gewinn, gesamt)
 * @param topLosers            Top Verlierer (höchster absoluter Verlust, gesamt)
 * @param belowPurchaseItems   Positionen mit aktuellem Wert unter Einkaufspreis
 * @param dailyTopGainers      Top Gewinner nach täglicher Preisänderung
 * @param dailyTopLosers       Top Verlierer nach täglicher Preisänderung
 */
public record DashboardDto(
    List<CategorySummary> categories,
    double totalInvested,
    double totalCurrentValue,
    double totalProfit,
    int totalBelowPurchase,
    List<ItemHighlight> topGainers,
    List<ItemHighlight> topLosers,
    List<ItemHighlight> belowPurchaseItems,
    List<ItemHighlight> dailyTopGainers,
    List<ItemHighlight> dailyTopLosers
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
   * @param invested     Einkaufspreis (oder Vortagespreis im täglichen Modus)
   * @param currentValue Aktueller Marktwert (oder heutiger Preis im täglichen Modus)
   * @param profit       Gewinn in EUR (negativ = Verlust)
   * @param linkUrl      Link zur zugehörigen Listenansicht
   * @param historyUrl   API-Pfad zur Preishistorie (null falls nicht verfügbar)
   */
  public record ItemHighlight(
      String category,
      String name,
      double invested,
      double currentValue,
      double profit,
      String linkUrl,
      String historyUrl
  ) {
  }
}
