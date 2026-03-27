package org.example.treasury.repository;

import java.util.Optional;
import org.example.treasury.persistence.JobRuntimeSettingsEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobRuntimeSettingsRepository extends MongoRepository<JobRuntimeSettingsEntity, String> {
  Optional<JobRuntimeSettingsEntity> findByKey(String key);
}

