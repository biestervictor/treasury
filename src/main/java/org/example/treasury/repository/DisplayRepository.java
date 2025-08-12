package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.Display;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository-Interface für die Display-Entität.
 * Diese Schnittstelle erweitert MongoRepository und bietet CRUD-Operationen für Displays.
 */

@Repository

public interface DisplayRepository extends MongoRepository<Display, String> {
  /**
   * Benutzerdefinierte Abfrage, um Displays nach Typ zu finden.
   *
   * @param type Typ des Displays
   * @return Liste von Displays mit dem angegebenen Typ
   */
  List<Display> findByType(String type);

  /**
   * Benutzerdefinierte Abfrage, um Displays nach Wertbereich zu finden.
   *
   * @param minValue minimaler Wert
   * @param maxValue maximaler Wert
   * @return Liste von Displays, deren Wert im angegebenen Bereich liegt
   */
  List<Display> findByValueBoughtBetween(double minValue, double maxValue);

  /**
   * Benutzerdefinierte Abfrage, um Displays nach setCode
   * zu finden (Groß-/Kleinschreibung ignorieren).
   *
   * @param setCode Set-Code des Displays
   * @return Liste von Displays mit dem angegebenen Set-Code
   */
  @Query("{ 'setCode': { $regex: ?0, $options: 'i' } }")
  List<Display> findBySetCodeIgnoreCase(String setCode);

  List<Display> findBySetCodeIgnoreCaseAndTypeIgnoreCase(String setCode, String type);

  List<Display> findByTypeIgnoreCase(String type);
  List<Display> findAll();
}