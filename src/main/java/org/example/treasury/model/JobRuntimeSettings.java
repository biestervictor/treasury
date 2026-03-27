package org.example.treasury.model;

import java.time.Instant;

/**
 * Persistierbares Runtime-Settings-Objekt pro Job.
 *
 * <p>Aktuell wird es in-memory gehalten. Später kann es als Mongo/JPA Entity genutzt werden.</p>
 */
public record JobRuntimeSettings(
    JobKey key,
    boolean enabled,
    /** Cron im Spring-Format inkl. Sekundenfeld. */
    String cron,
    /** Zeitzone-ID (optional, null = System Default). */
    String zoneId,
    Instant updatedAt
) {
}

