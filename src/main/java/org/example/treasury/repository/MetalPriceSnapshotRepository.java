package org.example.treasury.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.treasury.model.MetalPriceSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetalPriceSnapshotRepository extends MongoRepository<MetalPriceSnapshot, String> {
  Optional<MetalPriceSnapshot> findTopByOrderByTimestampDesc();

  long deleteByTimestampBefore(Instant timestamp);
}

