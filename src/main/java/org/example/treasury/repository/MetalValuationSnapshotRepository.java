package org.example.treasury.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.treasury.model.MetalValuationSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MetalValuationSnapshotRepository extends MongoRepository<MetalValuationSnapshot, String> {

  Optional<MetalValuationSnapshot> findTopByOrderByTimestampDesc();

  List<MetalValuationSnapshot> findAllByOrderByTimestampAsc();

  long deleteByTimestampBefore(Instant timestamp);
}

