package org.example.treasury.repository;

import java.util.List;
import java.util.Optional;
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

  /**
   * Der zuletzt gespeicherte Preis einer Münze von einer bestimmten Quelle.
   * Wird für den Vorher-/Nachher-Vergleich in der Scraper-History verwendet.
   *
   * @param preciousMetalId MongoDB-ID der Münze
   * @param source          Preisquelle
   * @return neuester Preis-Eintrag (optional)
   */
  Optional<CollectorCoinPrice> findTopByPreciousMetalIdAndSourceOrderByTimestampDesc(
      String preciousMetalId, CollectorCoinPriceSource source);

  /**
   * Löscht alle Preis-Einträge einer Münze (Cascade beim Löschen der Münze).
   *
   * @param preciousMetalId MongoDB-ID der zu löschenden Münze
   */
  void deleteByPreciousMetalId(String preciousMetalId);
}
