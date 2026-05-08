package org.example.treasury.repository;

import java.util.Optional;
import org.example.treasury.persistence.AppConfigEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository für applikationsweite Konfigurationseinträge. */
public interface AppConfigRepository extends MongoRepository<AppConfigEntry, String> {

  /**
   * Sucht einen Konfigurationseintrag anhand des Schlüssels.
   *
   * @param key Konfigurationsschlüssel
   * @return Eintrag, falls vorhanden
   */
  Optional<AppConfigEntry> findByKey(String key);
}
