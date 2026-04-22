package org.example.treasury.repository;

import java.util.List;
import org.example.treasury.model.SecretLair;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository-Interface für die SecretLair-Entität.
 * Diese Schnittstelle erweitert MongoRepository und bietet CRUD-Operationen für SecretLair.
 */

@Repository

public interface SecretLairRepository extends MongoRepository<SecretLair, String> {

  /**
   * Finds all non-sold Secret Lairs with the given name.
   *
   * @param name the product name
   * @return list of matching active Secret Lairs
   */
  List<SecretLair> findByNameAndIsSoldFalse(String name);

}
