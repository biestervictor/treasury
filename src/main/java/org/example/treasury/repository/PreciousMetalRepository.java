package org.example.treasury.repository;

import org.example.treasury.model.PreciousMetal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * PreciousMetalRepository is an interface that extends MongoRepository to provide CRUD operations
 * for the PreciousMetal entity.
 */

@Repository

public interface PreciousMetalRepository extends MongoRepository<PreciousMetal, String> {
}