package org.example.treasury.model;

import java.time.Instant;

/**
 * Persistierbares Settings-Model pro Job.
 */
public record JobSetting(
    JobKey key,
    String displayName,
    boolean enabled,
    JobSchedule schedule,
    Instant lastTriggeredAt,
    Instant updatedAt
) {
}

