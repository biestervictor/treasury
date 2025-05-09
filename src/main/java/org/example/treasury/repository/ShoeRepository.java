package org.example.treasury.repository;


import org.example.treasury.model.Shoe;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoeRepository extends MongoRepository<Shoe, String> {

}