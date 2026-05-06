package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.model.CollectorScraperRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link CollectorScraperRun} documents.
 */
@Repository
public interface CollectorScraperRunRepository
    extends MongoRepository<CollectorScraperRun, String> {

  /**
   * All runs, newest first.
   *
   * @return runs ordered by timestamp descending
   */
  List<CollectorScraperRun> findAllByOrderByTimestampDesc();

  /**
   * Runs for a specific source, newest first.
   *
   * @param source the source to filter by
   * @return runs for that source ordered by timestamp descending
   */
  List<CollectorScraperRun> findBySourceOrderByTimestampDesc(CollectorCoinPriceSource source);
}
