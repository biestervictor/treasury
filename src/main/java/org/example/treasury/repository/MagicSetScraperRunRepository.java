package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.MagicSetScraperRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository für {@link MagicSetScraperRun}-Dokumente.
 */
@Repository
public interface MagicSetScraperRunRepository extends MongoRepository<MagicSetScraperRun, String> {

  /**
   * Alle Läufe, neueste zuerst.
   *
   * @return Läufe absteigend nach Zeitstempel sortiert
   */
  List<MagicSetScraperRun> findAllByOrderByTimestampDesc();
}
