package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.CollectorCoinPrice;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository für Sammlermünz-Preisverläufe.
 */
public interface CollectorCoinPriceRepository extends MongoRepository<CollectorCoinPrice, String> {

  /**
   * Alle Preis-Einträge einer Münze, chronologisch aufsteigend.
   *
   * @param preciousMetalId MongoDB-ID der Münze
   * @return Liste der Einträge
   */
  List<CollectorCoinPrice> findByPreciousMetalIdOrderByTimestampAsc(String preciousMetalId);

  /**
   * Preis-Einträge einer Münze von einer bestimmten Quelle, chronologisch aufsteigend.
   *
   * @param preciousMetalId MongoDB-ID der Münze
   * @param source          Preisquelle
   * @return Liste der Einträge
   */
  List<CollectorCoinPrice> findByPreciousMetalIdAndSourceOrderByTimestampAsc(
      String preciousMetalId, CollectorCoinPriceSource source);
}
