package org.example.treasury.service;

import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.treasury.dto.MailRequest;
import org.example.treasury.model.CardMarketPriceSnapshot;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages wish prices for Magic sets and triggers email notifications
 * when the current Cardmarket price falls at or below a configured wish price.
 * Prices are tracked per set code and display type (e.g. DRAFT, COLLECTOR).
 */
@Service
public class WishPriceService {

  private static final Logger logger = LoggerFactory.getLogger(WishPriceService.class);

  private final MagicSetService magicSetService;
  private final DisplayPriceCollectorService displayPriceCollectorService;
  private final CardMarketPriceHistoryService priceHistoryService;

  @Autowired(required = false)
  private MailService mailService;

  @Value("${treasury.mail.startup.to:victor.biester@icloud.com}")
  private String notificationTo;

  /**
   * Constructor for WishPriceService.
   *
   * @param magicSetService              the magic set service
   * @param displayPriceCollectorService the Cardmarket scraper service
   * @param priceHistoryService          the price history service
   */
  public WishPriceService(MagicSetService magicSetService,
                          DisplayPriceCollectorService displayPriceCollectorService,
                          CardMarketPriceHistoryService priceHistoryService) {
    this.magicSetService = magicSetService;
    this.displayPriceCollectorService = displayPriceCollectorService;
    this.priceHistoryService = priceHistoryService;
  }

  /**
   * Persists a wish price for the given set code and display type.
   * A price of {@code 0} or negative removes the entry for that type.
   *
   * @param setCode   the set code (uppercase)
   * @param type      the display type (e.g. "DRAFT", "COLLECTOR")
   * @param wishPrice the desired maximum purchase price in EUR
   */
  public void setWishPrice(String setCode, String type, double wishPrice) {
    List<MagicSet> sets = magicSetService.getMagicSetByCode(setCode);
    if (sets.isEmpty()) {
      logger.warn("setWishPrice: no set found for code {}", setCode);
      return;
    }
    MagicSet set = sets.getFirst();
    Map<String, Double> wishPrices = set.getWishPrices();
    if (wishPrices == null) {
      wishPrices = new HashMap<>();
    }
    String normalizedType = type.toUpperCase();
    if (wishPrice <= 0) {
      wishPrices.remove(normalizedType);
    } else {
      wishPrices.put(normalizedType, wishPrice);
    }
    set.setWishPrices(wishPrices.isEmpty() ? null : wishPrices);
    magicSetService.saveMagicSet(set);
    logger.info("Wish price for {}/{} set to {}",
        setCode, type, wishPrice <= 0 ? "null (removed)" : wishPrice);
  }

  /**
   * Removes the wish price for the given set code and display type.
   *
   * @param setCode the set code (uppercase)
   * @param type    the display type (e.g. "DRAFT", "COLLECTOR")
   */
  public void removeWishPrice(String setCode, String type) {
    List<MagicSet> sets = magicSetService.getMagicSetByCode(setCode);
    if (sets.isEmpty()) {
      logger.warn("removeWishPrice: no set found for code {}", setCode);
      return;
    }
    MagicSet set = sets.getFirst();
    Map<String, Double> wishPrices = set.getWishPrices();
    if (wishPrices == null || wishPrices.isEmpty()) {
      return;
    }
    wishPrices.remove(type.toUpperCase());
    set.setWishPrices(wishPrices.isEmpty() ? null : wishPrices);
    magicSetService.saveMagicSet(set);
    logger.info("Wish price for {}/{} removed", setCode, type);
  }

  /**
   * Returns the full price history for the given set code and display type,
   * ordered by date ascending.
   * The item key stored in the snapshot repository is {@code "setCode|type"}.
   *
   * @param setCode the set code (uppercase)
   * @param type    the display type (e.g. "DRAFT", "COLLECTOR")
   * @return list of price snapshots
   */
  public List<CardMarketPriceSnapshot> getPriceHistory(String setCode, String type) {
    String itemId = setCode.toUpperCase() + "|" + type.toUpperCase();
    return priceHistoryService.getHistory(itemId);
  }

  /**
   * Scrapes current Cardmarket prices for all sets that have at least one wish price configured,
   * saves price snapshots, and sends an email notification for each set/type whose scraped
   * price is at or below the configured wish price.
   */
  public void checkPricesAndNotify() {
    LocalDate today = LocalDate.now();

    List<MagicSet> setsWithWishPrices = magicSetService.getAllMagicSets().stream()
        .filter(s -> s.getWishPrices() != null && !s.getWishPrices().isEmpty())
        .toList();

    if (setsWithWishPrices.isEmpty()) {
      logger.info("WishPriceService: keine Sets mit Wunschpreis konfiguriert – nichts zu tun");
      return;
    }

    LocalDate znnReleaseDate;
    try {
      znnReleaseDate = magicSetService.getMagicSetByCode("ZNR").getFirst().getReleaseDate();
    } catch (Exception e) {
      logger.error("WishPriceService: ZNR-Set nicht gefunden – Legacy-Grenze unbekannt", e);
      return;
    }

    List<String> alerts = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
      for (MagicSet set : setsWithWishPrices) {
        for (Map.Entry<String, Double> entry : set.getWishPrices().entrySet()) {
          String type = entry.getKey();
          Double wishPrice = entry.getValue();
          if (wishPrice == null || wishPrice <= 0) {
            continue;
          }

          Display tempDisplay = new Display();
          tempDisplay.setSetCode(set.getCode().toUpperCase());
          tempDisplay.setName(set.getName());
          tempDisplay.setType(type);
          tempDisplay.setLanguage("EN");

          boolean isLegacy = set.getReleaseDate() != null
              && set.getReleaseDate().isBefore(znnReleaseDate);

          try {
            displayPriceCollectorService.runScraper(playwright, tempDisplay, isLegacy);
          } catch (Exception e) {
            logger.error("WishPriceService: Scraping-Fehler für {}/{}", set.getCode(), type, e);
            continue;
          }

          Double currentPrice = tempDisplay.getRelevantPreis();
          if (currentPrice == null || currentPrice <= 0) {
            logger.info("WishPriceService: kein Preis ermittelt für {}/{}", set.getCode(), type);
            continue;
          }

          logger.info("WishPriceService: {}/{} aktuell {} € (Wunsch {} €)",
              set.getCode(), type,
              String.format("%.2f", currentPrice), String.format("%.2f", wishPrice));

          if (currentPrice <= wishPrice) {
            String msg = String.format("%s (%s/%s): %.2f \u20ac \u2264 Wunschpreis %.2f \u20ac",
                set.getName(), set.getCode(), type, currentPrice, wishPrice);
            alerts.add(msg);
            logger.info("WishPrice alert: {}", msg);
          }
        }
      }
    } catch (Exception e) {
      logger.error("WishPriceService: Playwright-Session fehlgeschlagen", e);
    }

    if (!alerts.isEmpty()) {
      sendAlertMail(alerts, today);
    }

    logger.info("WishPrice check beendet – {} Alarm(e)", alerts.size());
  }

  private void sendAlertMail(List<String> alerts, LocalDate date) {
    if (mailService == null) {
      logger.info("MailService nicht verfügbar – WishPrice-Benachrichtigung übersprungen");
      return;
    }
    String subject = "Treasury: Wunschpreis-Alarm " + date;
    String body = "Folgende Sets haben ihren Wunschpreis erreicht oder unterschritten:\n\n"
        + String.join("\n", alerts)
        + "\n\nBitte den Wunschpreis in Treasury anpassen, "
        + "um weitere Benachrichtigungen zu vermeiden.";
    try {
      mailService.send(new MailRequest(List.of(notificationTo), subject, body));
    } catch (Exception e) {
      logger.warn("Konnte Wunschpreis-Alarm-Mail nicht senden: {}", e.getMessage());
    }
  }
}
