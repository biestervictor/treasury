package org.example.treasury.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.treasury.model.PreciousMetal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * PreciousMetalRepository is an interface that extends MongoRepository to provide CRUD operations
 * for the PreciousMetal entity.
 */

@Repository

public interface PreciousMetalRepository extends MongoRepository<PreciousMetal, String> {

  List<PreciousMetal> findAllByImportedAtBetween(LocalDate from, LocalDate to);

  List<PreciousMetal> findAllByImportedAtIsNotNull();

  Optional<PreciousMetal> findByImportKey(String importKey);

  boolean existsByImportKey(String importKey);
}