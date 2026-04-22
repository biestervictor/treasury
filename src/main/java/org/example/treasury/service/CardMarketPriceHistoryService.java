package org.example.treasury.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
   * Returns items that have snapshots for both today and yesterday,
   * representing the daily price change.
   *
   * @return list of daily changes ordered by absolute change descending
   */
  public List<DailyChange> getDailyChanges() {
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);

    List<CardMarketPriceSnapshot> todaySnaps = repository.findByDate(today);
    if (todaySnaps.isEmpty()) {
      return List.of();
    }

    List<CardMarketPriceSnapshot> yesterdaySnaps = repository.findByDate(yesterday);
    Map<String, CardMarketPriceSnapshot> yesterdayByItemId = yesterdaySnaps.stream()
        .collect(Collectors.toMap(CardMarketPriceSnapshot::getItemId, s -> s));

    return todaySnaps.stream()
        .filter(s -> yesterdayByItemId.containsKey(s.getItemId()))
        .filter(s -> yesterdayByItemId.get(s.getItemId()).getPrice() > 0)
        .map(s -> new DailyChange(
            s.getItemId(),
            s.getItemType(),
            yesterdayByItemId.get(s.getItemId()).getPrice(),
            s.getPrice()))
        .toList();
  }

  /**
   * Represents the price change of one item between yesterday and today.
   *
   * @param itemId      the item key (setCode|type for Display, MongoDB ID for SecretLair)
   * @param itemType    "DISPLAY" or "SECRET_LAIR"
   * @param prevPrice   yesterday's price in EUR
   * @param currentPrice today's price in EUR
   */
  public record DailyChange(
      String itemId,
      String itemType,
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
