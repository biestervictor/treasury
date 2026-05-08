package org.example.treasury.service;

import java.time.LocalDate;
import java.util.Map;
import org.example.treasury.model.PreciousMetalType;
import org.springframework.stereotype.Service;

import static java.util.Map.entry;

/**
 * Liefert historische Jahresdurchschnittswerte für Gold und Silber in EUR/oz (Troy-Unze).
 *
 * <p>Die Werte basieren auf den jährlichen USD/oz-Durchschnitten der LBMA
 * (London Bullion Market Association) und den entsprechenden EUR/USD-Jahresdurchschnittskursen
 * der EZB. Referenzkontrolle gegen boerse.de Jahres-Schlusskurse (XC0009653103 / XC0009655157).
 * Für laufende Jahre wird ein gleitender Schätzwert auf Basis der bisherigen Monatsdurchschnitte
 * verwendet.</p>
 *
 * <p>Diese Tabelle ermöglicht die Aufschlüsselung des Gewinns einer Sammelmünze in:
 * <ul>
 *   <li><b>Spot-Anteil:</b> Wertsteigerung durch gestiegene Metallpreise</li>
 *   <li><b>Aufpreis-Anteil:</b> Wertsteigerung durch höheres Sammlerpremium (Limitierung, Box, etc.)</li>
 * </ul>
 * </p>
 */
@Service
public class HistoricalSpotPriceService {

  private static final double GRAMS_PER_TROY_OUNCE = 31.1034768;

  /**
   * Jahresdurchschnitt Gold EUR/oz.
   * Quellen: LBMA Gold Price (USD) / EZB EUR-USD-Jahresdurchschnitt,
   * kontrolliert gegen boerse.de Jahres-Schlusskurse (ISIN XC0009655157).
   */
  private static final Map<Integer, Double> GOLD_EUR_PER_OZ = Map.ofEntries(
      entry(2009, 697.0),
      entry(2010, 924.0),
      entry(2011, 1129.0),
      entry(2012, 1299.0),
      entry(2013, 1062.0),
      entry(2014, 953.0),
      entry(2015, 1069.0),
      entry(2016, 1130.0),
      entry(2017, 1112.0),
      entry(2018, 1074.0),
      entry(2019, 1243.0),
      entry(2020, 1551.0),
      entry(2021, 1520.0),
      entry(2022, 1709.0),
      entry(2023, 1794.0),
      entry(2024, 2203.0),
      entry(2025, 3000.0),  // Schluss 3.670 EUR; Jahresstart ~2.508 EUR; Durchschnitt ca. 3.000
      entry(2026, 4100.0)   // Schätzung Jan-Mai 2026: Ø ~4.110 EUR (boerse.de Monatsdaten)
  );

  /**
   * Jahresdurchschnitt Silber EUR/oz.
   * Quellen: LBMA Silver Price (USD) / EZB EUR-USD-Jahresdurchschnitt,
   * kontrolliert gegen boerse.de Jahres-Schlusskurse (ISIN XC0009653103).
   */
  private static final Map<Integer, Double> SILVER_EUR_PER_OZ = Map.ofEntries(
      entry(2009, 10.5),
      entry(2010, 15.2),
      entry(2011, 25.2),
      entry(2012, 24.2),
      entry(2013, 17.9),
      entry(2014, 14.4),
      entry(2015, 14.5),
      entry(2016, 15.4),
      entry(2017, 15.1),
      entry(2018, 13.3),
      entry(2019, 14.5),
      entry(2020, 18.0),
      entry(2021, 21.2),
      entry(2022, 20.7),
      entry(2023, 21.6),
      entry(2024, 26.1),
      entry(2025, 40.0),  // Schluss 61,27 EUR; Jahresstart ~27,76 EUR; Durchschnitt ca. 40
      entry(2026, 71.0)   // Schätzung Jan-Mai 2026: Ø ~71 EUR (boerse.de Monatsdaten)
  );

  /**
   * Gibt den historischen Spot-Einheitswert (EUR) für eine Münze zum Kaufzeitpunkt zurück.
   * Verwendet den Jahresdurchschnitt des Kaufjahres. Jahre vor 2009 und nach 2025
   * werden auf den nächsten verfügbaren Wert geclampt.
   *
   * @param type          Metalltyp (GOLD oder SILVER)
   * @param purchaseDate  Kaufdatum der Münze
   * @param weightInGrams Gewicht der Münze in Gramm
   * @return geschätzter Spot-Wert in EUR zum Kaufjahr, oder 0.0 wenn nicht berechenbar
   */
  public double getPurchaseSpotUnitValue(PreciousMetalType type,
                                          LocalDate purchaseDate,
                                          double weightInGrams) {
    if (purchaseDate == null || weightInGrams <= 0 || type == null) {
      return 0.0;
    }
    int year = purchaseDate.getYear();
    Map<Integer, Double> table = (type == PreciousMetalType.GOLD)
        ? GOLD_EUR_PER_OZ : SILVER_EUR_PER_OZ;

    // Auf verfügbaren Bereich clampen
    int clampedYear = Math.max(2009, Math.min(2026, year));
    Double eurPerOz = table.get(clampedYear);
    if (eurPerOz == null) {
      return 0.0;
    }
    return (weightInGrams / GRAMS_PER_TROY_OUNCE) * eurPerOz;
  }

  /**
   * Gibt den Jahresdurchschnitt EUR/oz für den angegebenen Metalltyp und das Jahr zurück.
   * Gibt 0.0 zurück wenn das Jahr außerhalb des bekannten Bereichs liegt.
   *
   * @param type Metalltyp
   * @param year Jahreszahl (z.B. 2020)
   * @return EUR/oz Jahresdurchschnitt oder 0.0
   */
  public double getAnnualAverageEurPerOz(PreciousMetalType type, int year) {
    Map<Integer, Double> table = (type == PreciousMetalType.GOLD)
        ? GOLD_EUR_PER_OZ : SILVER_EUR_PER_OZ;
    return table.getOrDefault(year, 0.0);
  }
}
