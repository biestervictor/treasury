package org.example.treasury.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.treasury.model.CardMarketPriceSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@link CardMarketPriceSnapshot} documents.
 */
public interface CardMarketPriceSnapshotRepository
    extends MongoRepository<CardMarketPriceSnapshot, String> {

  /**
   * Returns all snapshots for the given item ordered by date ascending.
   *
   * @param itemId the item key
   * @return list of snapshots
   */
  List<CardMarketPriceSnapshot> findByItemIdOrderByDateAsc(String itemId);

  /**
   * Finds an existing snapshot for a given item on a specific date (for idempotency).
   *
   * @param itemId the item key
   * @param date   the snapshot date
   * @return the snapshot if present
   */
  Optional<CardMarketPriceSnapshot> findByItemIdAndDate(String itemId, LocalDate date);

  /**
   * Returns all snapshots for a given date across all items.
   *
   * @param date the snapshot date
   * @return list of snapshots
   */
  List<CardMarketPriceSnapshot> findByDate(LocalDate date);

  /**
   * Finds the most recent snapshot for an item strictly before the given date.
   *
   * @param itemId the item key
   * @param date   exclusive upper bound date
   * @return the most recent snapshot before the given date, if any
   */
  Optional<CardMarketPriceSnapshot> findTopByItemIdAndDateBeforeOrderByDateDesc(
      String itemId, LocalDate date);

  /**
   * Finds the most recent snapshot for an item across all dates.
   *
   * @param itemId the item key
   * @return the most recent snapshot, if any
   */
  Optional<CardMarketPriceSnapshot> findTopByItemIdOrderByDateDesc(String itemId);
}
