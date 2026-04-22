package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an aggregated view of Secret Lair positions grouped by name.
 * Contains totals and averages for all non-sold copies of a given product.
 */
@Getter
@Setter
public class AggregatedSecretLair {

  private String name;
  private long count;
  private double averagePrice;
  private double currentValue;
  private String imageUrl;
}
