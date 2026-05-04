package org.example.treasury.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Preis-Datenpunkt für eine Sammlermünze von einer externen Quelle.
 * Wird vom CollectorCoinPricingService befüllt und dient als Grundlage
 * für den Preisverlauf-Chart pro Münze.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collectorCoinPrice")
public class CollectorCoinPrice {

  @Id
  private String id;

  /** Referenz auf PreciousMetal.id. */
  private String preciousMetalId;

  /** Name der Münze zum Zeitpunkt des Scrapes (denormalisiert für einfachen Zugriff). */
  private String preciousMetalName;

  /** Quelle des Preises. */
  private CollectorCoinPriceSource source;

  /** Ermittelter Marktpreis pro Stück (EUR). */
  private double priceEur;

  /** URL, von der der Preis stammt (optional, für Nachvollziehbarkeit). */
  private String sourceUrl;

  /** Optionale Anmerkungen (z.B. „Ø aus 3 Verkäufen", „günstigstes Angebot"). */
  private String notes;

  /** Zeitpunkt des Scrapes. */
  private Instant timestamp;
}
