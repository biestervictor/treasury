package org.example.treasury.model;

import java.time.ZoneId;

/**
 * Beschreibt das geplante Ausführungsintervall eines Jobs.
 *
 * <p>Persistierbar ausgelegt (primitive Felder), Berechnung der "nächsten Ausführung" erfolgt separat.</p>
 */
public record JobSchedule(
    /** "cron" oder "fixed" (für spätere Erweiterungen). */
    String type,
    /** Cron Expression im Spring-Format (Sekundenfeld inklusive). */
    String cron,
    /** Menschliche Beschreibung fürs UI (z.B. "täglich 00:00"). */
    String intervalText,
    /** Zeitzone für die Berechnung der nächsten Ausführung. */
    ZoneId zoneId
) {
  public static JobSchedule cron(String cron, String intervalText) {
    return new JobSchedule("cron", cron, intervalText, ZoneId.systemDefault());
  }
}

