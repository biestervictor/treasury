package org.example.treasury.model;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * PreciousMetal (Edelmetall) repräsentiert eine Zeile aus der Edelmetalle.csv.
 */

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "preciousMetal")

public class PreciousMetal {
  @Id
  private String id;

  /**
   * Deterministischer Import-Schlüssel zur Duplikatvermeidung.
   * Wird aus den fachlichen Feldern gebildet und muss beim erneuten Import identisch sein.
   */
  private String importKey;

  /** Bezeichnung/Name (CSV: Bezeichnung). */
  private String name;

  /** Erscheinungsjahr, optional. */
  private Integer year;

  /** Gewicht in Gramm (CSV: Gewicht in Gramm). */
  private double weightInGrams;

  /** Anzahl (CSV: Anzahl). */
  private int quantity;

  /** Typ: GOLD oder SILVER. */
  private PreciousMetalType type;

  /** Einkaufspreis pro Einheit (nicht Gesamtwert). */
  private double purchasePrice;

  /** Datum des Imports (für Gewinnverlauf/Timeline). */
  private LocalDate importedAt;

  /**
   * Manuell gesetzter Sammlerwert pro Stück (EUR).
   * 0.0 bedeutet „nicht gesetzt" → Fallback auf Spot-basierten Wert.
   */
  private double marketValue;

}