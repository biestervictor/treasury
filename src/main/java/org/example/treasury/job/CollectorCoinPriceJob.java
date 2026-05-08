package org.example.treasury.job;

import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.service.CollectorCoinPricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job: aktualisiert nächtlich Sammlermünz-Preise aus allen konfigurierten Quellen.
 *
 * <p>Läuft täglich um 02:00 Uhr. Kann per Controller-Endpoint auch manuell
 * pro Quelle oder für alle Quellen getriggert werden.</p>
 */
@Component
public class CollectorCoinPriceJob {

  private static final Logger log = LoggerFactory.getLogger(CollectorCoinPriceJob.class);

  private final CollectorCoinPricingService pricingService;

  /**
   * Konstruktor.
   *
   * @param pricingService Service für Sammlermünz-Preisermittlung
   */
  public CollectorCoinPriceJob(CollectorCoinPricingService pricingService) {
    this.pricingService = pricingService;
  }

  /**
   * Nächtlicher Lauf – alle Quellen sequenziell (täglich 02:00 UTC).
   */
  @Scheduled(cron = "0 0 2 * * *")
  public void executeNightly() {
    log.info("CollectorCoinPriceJob: nächtlicher Lauf gestartet");
    try {
      int count = pricingService.updateFromAllSources().size();
      log.info("CollectorCoinPriceJob: nächtlicher Lauf beendet – {} neue Einträge", count);
    } catch (Exception e) {
      log.warn("CollectorCoinPriceJob: nächtlicher Lauf fehlgeschlagen: {}", e.getMessage());
    }
  }

  /**
   * Manuelles Triggern einer einzelnen Quelle (z.B. per Dashboard-Button).
   *
   * @param source Quelle, die aktualisiert werden soll
   * @return Anzahl der neu gespeicherten Einträge
   */
  public int triggerSource(CollectorCoinPriceSource source) {
    log.info("CollectorCoinPriceJob: manueller Trigger für Quelle {}", source);
    try {
      int count = pricingService.updateFromSource(source).size();
      log.info("CollectorCoinPriceJob: Quelle {} fertig – {} Einträge", source, count);
      return count;
    } catch (Exception e) {
      log.warn("CollectorCoinPriceJob: Quelle {} fehlgeschlagen: {}", source, e.getMessage());
      return 0;
    }
  }

  /**
   * Manuelles Triggern aller Quellen.
   *
   * @return Gesamtanzahl neu gespeicherter Einträge
   */
  public int triggerAll() {
    log.info("CollectorCoinPriceJob: manueller Trigger für alle Quellen");
    int count = pricingService.updateFromAllSources().size();
    log.info("CollectorCoinPriceJob: alle Quellen fertig – {} Einträge", count);
    return count;
  }
}
