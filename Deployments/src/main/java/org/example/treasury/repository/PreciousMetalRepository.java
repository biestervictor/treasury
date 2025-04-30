package org.example.treasury.repository;

import org.example.treasury.model.PreciousMetal;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreciousMetalRepository extends MongoRepository<PreciousMetal, String> {
}
