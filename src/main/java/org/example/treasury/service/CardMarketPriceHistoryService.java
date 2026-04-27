package org.example.treasury.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.treasury.model.CardMarketPriceSnapshot;
import org.example.treasury.repository.CardMarketPriceSnapshotRepository;
import org.springframework.stereotype.Service;

/**
 * Manages historical price snapshots for Displays and SecretLairs.
 */
@Service
public class CardMarketPriceHistoryService {

  private final CardMarketPriceSnapshotRepository repository;

  /**
   * Constructor.
   *
   * @param repository the snapshot repository
   */
  public CardMarketPriceHistoryService(CardMarketPriceSnapshotRepository repository) {
    this.repository = repository;
  }

  /**
   * Saves a daily price snapshot (idempotent: updates existing entry for the same date).
   *
   * @param itemId   key — for Display: "setCode|type"; for SecretLair: MongoDB document ID
   * @param itemType "DISPLAY" or "SECRET_LAIR"
   * @param price    scraped relevant market price in EUR
   * @param date     date of the snapshot
   */
  public void saveSnapshot(String itemId, String itemType, double price, LocalDate date) {
    if (price <= 0) {
      return;
    }
    Optional<CardMarketPriceSnapshot> existing = repository.findByItemIdAndDate(itemId, date);
    if (existing.isPresent()) {
      CardMarketPriceSnapshot snapshot = existing.get();
      snapshot.setPrice(price);
      repository.save(snapshot);
    } else {
      repository.save(CardMarketPriceSnapshot.builder()
          .itemId(itemId)
          .itemType(itemType)
          .date(date)
          .price(price)
          .build());
    }
  }

  /**
   * Returns the full price history for an item, ordered by date ascending.
   *
   * @param itemId the item key
   * @return list of snapshots sorted by date ascending
   */
  public List<CardMarketPriceSnapshot> getHistory(String itemId) {
    return repository.findByItemIdOrderByDateAsc(itemId);
  }

  /**
   * Returns the most recent price for each of the given item IDs.
   * Items without any snapshot are omitted from the result map.
   *
   * @param itemIds list of item keys (e.g. "ZNR|DRAFT")
   * @return map of itemId to its latest scraped price
   */
  public Map<String, Double> getLatestPricesByItemIds(List<String> itemIds) {
    Map<String, Double> result = new HashMap<>();
    for (String itemId : itemIds) {
      repository.findTopByItemIdOrderByDateDesc(itemId)
          .ifPresent(s -> result.put(itemId, s.getPrice()));
    }
    return result;
  }

  /**
   * Returns items that have a today-snapshot and at least one earlier snapshot,
   * representing the price change since the last available prior snapshot.
   * This is robust against missed scraper runs: instead of requiring strictly
   * yesterday's data, it uses the most recent snapshot before today.
   *
   * @return list of daily changes
   */
  public List<DailyChange> getDailyChanges() {
    LocalDate today = LocalDate.now();

    List<CardMarketPriceSnapshot> todaySnaps = repository.findByDate(today);
    if (todaySnaps.isEmpty()) {
      return List.of();
    }

    return todaySnaps.stream()
        .flatMap(s -> repository
            .findTopByItemIdAndDateBeforeOrderByDateDesc(s.getItemId(), today)
            .filter(prev -> prev.getPrice() > 0)
            .map(prev -> new DailyChange(
                s.getItemId(),
                s.getItemType(),
                prev.getDate(),
                prev.getPrice(),
                s.getPrice()))
            .stream())
        .toList();
  }

  /**
   * Represents the price change of one item between the last available prior snapshot and today.
   *
   * @param itemId       the item key (setCode|type for Display, MongoDB ID for SecretLair)
   * @param itemType     "DISPLAY" or "SECRET_LAIR"
   * @param prevDate     date of the comparison snapshot (may be earlier than yesterday)
   * @param prevPrice    price on prevDate in EUR
   * @param currentPrice today's price in EUR
   */
  public record DailyChange(
      String itemId,
      String itemType,
      LocalDate prevDate,
      double prevPrice,
      double currentPrice
  ) {

    /**
     * Absolute price change (currentPrice - prevPrice).
     *
     * @return absolute change in EUR
     */
    public double absoluteChange() {
      return currentPrice - prevPrice;
    }
  }
}
