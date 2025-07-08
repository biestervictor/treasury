package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;

/**
 * AggregatedDisplay is a class that represents the aggregated display of a set of cards.
 * It contains information about the set code, type, count, average price, and icon URI.
 */

@Getter
@Setter
public class AggregatedDisplay {
  private String setCode;
  private String type;
  private long count;
  private double averagePrice;
  private String iconUri;
  private double sanitizedMarketPrice;
}