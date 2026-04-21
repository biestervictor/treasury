package org.example.treasury.service;

import java.time.LocalDate;
import java.util.List;
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
}
