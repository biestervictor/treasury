package org.example.treasury.model;

import java.time.Instant;
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
@Document(collection = "metalPriceSnapshot")
public class MetalPriceSnapshot {

  @Id
  private String id;

  /** Zeitstempel der Preisabfrage. */
  private Instant timestamp;

  /** Preis pro Unze (EUR) laut externer API. */
  private double goldPriceEurPerOunce;

  /** Preis pro Unze (EUR) laut externer API. */
  private double silverPriceEurPerOunce;
}

