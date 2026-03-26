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
  }
}

