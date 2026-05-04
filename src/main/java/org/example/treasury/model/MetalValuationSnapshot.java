package org.example.treasury.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "metalValuationSnapshot")
public class MetalValuationSnapshot {

  @Id
  private String id;

  /** Zeitpunkt der Bewertung. */
  private Instant timestamp;

  /** Bewertungen pro Position (pro Münze/Produkt). */
  private List<ItemValuation> items;

  private double totalCurrentValue;

  private double totalProfit;

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ItemValuation {

    /** Referenz auf PreciousMetal.id (kann null sein, falls noch nicht persistiert). */
    private String preciousMetalId;

    private String name;

    private PreciousMetalType type;

    private double weightInGrams;

    private int quantity;

    /** Aktueller Wert pro Unze zum Zeitpunkt (EUR/oz). */
    private double priceEurPerOunce;

    /** Aktueller Wert pro Einheit (EUR). */
    private double currentUnitValue;

    /** Aktueller Gesamtwert (EUR). */
    private double currentTotalValue;

    /** Einkaufspreis pro Einheit (EUR). */
    private double purchasePrice;

    /** Gewinn/Gesamtprofit dieser Position (EUR). */
    private double profit;

    /**
     * Manuell gesetzter Sammlerwert pro Stück (EUR), 0 wenn nicht gesetzt.
     * Entspricht dem Rohwert aus {@link PreciousMetal#getMarketValue()}.
     */
    private double marketValue;

    /**
     * True wenn für diese Position ein manueller Sammlerwert gesetzt ist
     * (d.h. {@link #marketValue} > 0) und dieser statt des Spot-Wertes
     * für {@link #currentUnitValue} verwendet wurde.
     */
    private boolean usesMarketValue;

    /**
     * Immer der rein Spot-basierte Wert pro Stück (EUR), unabhängig davon ob
     * ein Sammlerwert gesetzt ist. Dient zur Vergleichsanzeige im Dashboard.
     */
    private double spotUnitValue;
  }
}

