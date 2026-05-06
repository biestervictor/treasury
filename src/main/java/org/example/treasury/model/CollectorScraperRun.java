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

/**
 * Records one scraping run for a single source across all configured coins.
 * Stored in MongoDB for historical analysis: which sources yield results,
 * which coins fail, and how prices evolve over time.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collectorScraperRun")
public class CollectorScraperRun {

  @Id
  private String id;

  /** When the run was executed. */
  private Instant timestamp;

  /** Which data source was scraped. */
  private CollectorCoinPriceSource source;

  /** Total number of coins attempted. */
  private int attempted;

  /** How many coins returned a price above spot value. */
  private int succeeded;

  /** Per-coin results for this run. */
  private List<Entry> entries;

  /** Per-coin result detail for one scraping run. */
  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Entry {

    /** PreciousMetal MongoDB ID. */
    private String metalId;

    /** Coin name (denormalized for display). */
    private String metalName;

    /** The search term that was sent to the source. */
    private String searchTerm;

    /** True if a usable price (above spot value) was found. */
    private boolean success;

    /** Scraped price in EUR; {@code null} if not found or below spot. */
    private Double priceEur;

    /** The most recent stored price from the previous run for this source; {@code null} if first. */
    private Double previousPriceEur;
  }
}
