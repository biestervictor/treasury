package org.example.treasury.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Protokolliert einen einzelnen Lauf des {@link org.example.treasury.job.MagicSetJob}.
 * Wird in der MongoDB-Collection {@code magicSetScraperRun} gespeichert.
 */
@Document("magicSetScraperRun")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagicSetScraperRun {

  /** MongoDB-Dokument-ID. */
  @Id
  private String id;

  /** Startzeitpunkt des Laufs (UTC). */
  private Instant timestamp;

  /** Anzahl der von Scryfall gelieferten Sets. */
  private int setsTotal;

  /** {@code true} wenn der Job erfolgreich durchgelaufen ist. */
  private boolean success;

  /** Fehlermeldung bei nicht-erfolgreichem Lauf; {@code null} bei Erfolg. */
  private String errorMessage;

  /** Laufzeit des Jobs in Millisekunden. */
  private long durationMs;
}
