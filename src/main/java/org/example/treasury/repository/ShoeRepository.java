package org.example.treasury.repository;


import java.util.Optional;
import org.example.treasury.model.Shoe;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * ShoeRepository is an interface that extends MongoRepository to provide CRUD operations
 * for the Shoe entity.
 */

@Repository

public interface ShoeRepository extends MongoRepository<Shoe, String> {
  Optional<Shoe> findById(String shoeId);
}