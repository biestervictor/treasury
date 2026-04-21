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
}
