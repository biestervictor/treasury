package org.example.treasury.model;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stores a daily price snapshot for a Display or SecretLair scraped from Cardmarket.
 *
 * <p>For Displays the {@code itemId} is {@code "setCode|type"} (market price is shared
 * across all purchases of the same product). For SecretLairs the {@code itemId} is the
 * MongoDB document ID of the individual item.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cardMarketPriceSnapshot")
@CompoundIndex(name = "item_date_idx", def = "{'itemId': 1, 'date': 1}", unique = true)
public class CardMarketPriceSnapshot {

  @Id
  private String id;

  /** For Display: "setCode|type"; for SecretLair: the MongoDB document ID. */
  private String itemId;

  /** Discriminator: "DISPLAY" or "SECRET_LAIR". */
  private String itemType;

  /** Date of the scraped price. */
  private LocalDate date;

  /** Relevant market price in EUR (outlier-filtered). */
  private double price;
}
