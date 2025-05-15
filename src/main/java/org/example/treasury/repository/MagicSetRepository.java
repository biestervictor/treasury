package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.MagicSet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MagicSetRepository ist ein Interface, das die CRUD-Operationen für die MagicSet-Klasse definiert.
 * Es erweitert das MongoRepository-Interface von Spring Data MongoDB.
 *
 * @author Your Name
 */

@Repository

public interface MagicSetRepository extends MongoRepository<MagicSet, String> {
  /**
   * Benutzerdefinierte Abfrage, um alle MagicSets mit dem angegebenen Code zu finden.
   *
   * @param code der Code des MagicSets
   * @return liste von MagicSets mit dem angegebenen Code
   */
  List<MagicSet> findAllByCode(String code);

}