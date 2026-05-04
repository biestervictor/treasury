package org.example.treasury.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.example.treasury.persistence.AppConfigEntry;
import org.example.treasury.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

/**
 * Verwaltung applikationsweiter Konfigurationseinträge (key/value).
 *
 * <p>Werte werden beim Lesen direkt aus MongoDB geladen und beim Schreiben
 * gleichzeitig in einen In-Memory-Cache und MongoDB persistiert (write-through).</p>
 */
@Service
public class AppConfigService {

  /** Key für den Numista-API-Schlüssel. */
  public static final String KEY_NUMISTA_API_KEY = "numista.api-key";

  private final AppConfigRepository repo;
  private final ConcurrentHashMap<String, String> cache;

  /**
   * Konstruktor – lädt alle bestehenden Einträge aus MongoDB in den Cache.
   *
   * @param repo das AppConfig-Repository
   */
  public AppConfigService(AppConfigRepository repo) {
    this.repo = repo;
    this.cache = new ConcurrentHashMap<>();
    repo.findAll().forEach(e -> {
      if (e.getKey() != null && e.getValue() != null) {
        cache.put(e.getKey(), e.getValue());
      }
    });
  }

  /**
   * Liefert den Konfigurationswert für den gegebenen Schlüssel.
   *
   * @param key Konfigurationsschlüssel
   * @return Wert oder {@code ""} falls nicht konfiguriert
   */
  public String get(String key) {
    return cache.getOrDefault(key, "");
  }

  /**
   * Speichert einen Konfigurationswert (write-through: Cache + MongoDB).
   *
   * @param key   Konfigurationsschlüssel
   * @param value Konfigurationswert
   */
  public void set(String key, String value) {
    cache.put(key, value);
    AppConfigEntry entity = new AppConfigEntry(key, value, Instant.now());
    repo.findByKey(key).ifPresent(existing -> entity.setId(existing.getId()));
    repo.save(entity);
  }
}
