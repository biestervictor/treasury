package org.example.treasury.dto;

import java.util.List;

/**
 * DTO returned by the asset group endpoints.
 * Contains metadata and all active positions of the same price group
 * (same setCode+type for Display; same name for SecretLair).
 *
 * @param groupName    human-readable label (e.g. "MH3 Collector Booster")
 * @param category     asset category label (e.g. "MTG Display", "Secret Lair")
 * @param currentPrice current market value shared by all positions in the group
 * @param historyUrl   API path for the price-history chart data
 * @param priceUrl     API path for the PATCH price-update endpoint
 * @param items        all active (non-sold) positions in this group
 */
public record AssetGroupDto(
    String groupName,
    String category,
    double currentPrice,
    String historyUrl,
    String priceUrl,
    List<AssetItemDto> items
) {

  /**
   * A single position (document) within an asset group.
   *
   * @param id           MongoDB document ID
   * @param location     storage location (Lagerort), may be empty
   * @param valueBought  purchase price in EUR (EK-Preis)
   * @param currentValue current market value in EUR
   * @param profit       currentValue minus valueBought (negative = loss)
   * @param language     language of the item (e.g. "EN")
   * @param url          CardMarket product URL, may be empty
   */
  public record AssetItemDto(
      String id,
      String location,
      double valueBought,
      double currentValue,
      double profit,
      String language,
      String url
  ) {
  }
}
