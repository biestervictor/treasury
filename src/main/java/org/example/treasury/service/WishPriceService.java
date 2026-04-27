package org.example.treasury.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.treasury.dto.MailRequest;
import org.example.treasury.model.CardMarketPriceSnapshot;
import org.example.treasury.model.MagicSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages wish prices for missing Magic sets and triggers email notifications
 * when the current market price falls at or below the configured wish price.
 */
@Service
public class WishPriceService {

  private static final String ITEM_TYPE = "MAGIC_SET_BOX";
  private static final Logger logger = LoggerFactory.getLogger(WishPriceService.class);

  private final MagicSetService magicSetService;
  private final MtgStocksService mtgStocksService;
  private final CardMarketPriceHistoryService priceHistoryService;

  @Autowired(required = false)
  private MailService mailService;

  @Value("${treasury.mail.startup.to:victor.biester@icloud.com}")
  private String notificationTo;

  /**
   * Constructor for WishPriceService.
   *
   * @param magicSetService      the magic set service
   * @param mtgStocksService     the MTGStocks service
   * @param priceHistoryService  the price history service
   */
  public WishPriceService(MagicSetService magicSetService,
                          MtgStocksService mtgStocksService,
                          CardMarketPriceHistoryService priceHistoryService) {
    this.magicSetService = magicSetService;
    this.mtgStocksService = mtgStocksService;
    this.priceHistoryService = priceHistoryService;
  }

  /**
   * Persists a wish price for the given set code.
   * A value of {@code 0} clears the wish price.
   *
   * @param setCode   the set code (uppercase)
   * @param wishPrice the desired maximum purchase price in EUR
   */
  public void setWishPrice(String setCode, double wishPrice) {
    List<MagicSet> sets = magicSetService.getMagicSetByCode(setCode);
    if (sets.isEmpty()) {
      logger.warn("setWishPrice: no set found for code {}", setCode);
      return;
    }
    MagicSet set = sets.getFirst();
    set.setWishPrice(wishPrice <= 0 ? null : wishPrice);
    magicSetService.saveMagicSet(set);
    logger.info("Wish price for {} set to {}",
        setCode, wishPrice <= 0 ? "null (cleared)" : wishPrice);
  }

  /**
   * Fetches current booster box prices from MTGStocks, stores daily snapshots for all sets,
   * and sends email notifications for sets whose current price is at or below the wish price.
   */
  public void checkPricesAndNotify() {
    Map<String, Double> prices;
    try {
      prices = mtgStocksService.fetchBoosterBoxPrices();
    } catch (Exception e) {
      logger.error("WishPriceService: could not fetch prices from MTGStocks", e);
      return;
    }

    LocalDate today = LocalDate.now();
    List<MagicSet> allSets = magicSetService.getAllMagicSets();
    List<String> alerts = new ArrayList<>();

    for (MagicSet set : allSets) {
      String code = set.getCode().toUpperCase();
      Double currentPrice = prices.get(code);
      if (currentPrice == null || currentPrice <= 0) {
        continue;
      }

      // Persist daily snapshot
      priceHistoryService.saveSnapshot(code, ITEM_TYPE, currentPrice, today);

      // Check wish price
      if (set.getWishPrice() != null && set.getWishPrice() > 0
          && currentPrice <= set.getWishPrice()) {
        String msg = String.format("%s (%s): %.2f € ≤ Wunschpreis %.2f €",
            set.getName(), code, currentPrice, set.getWishPrice());
        alerts.add(msg);
        logger.info("WishPrice alert: {}", msg);
      }
    }

    if (!alerts.isEmpty()) {
      sendAlertMail(alerts, today);
    }

    logger.info("WishPrice check done: {} prices saved, {} alerts",
        prices.size(), alerts.size());
  }

  /**
   * Returns the full price history for the given set code, ordered by date ascending.
   *
   * @param setCode the set code (uppercase)
   * @return list of price snapshots
   */
  public List<CardMarketPriceSnapshot> getPriceHistory(String setCode) {
    return priceHistoryService.getHistory(setCode.toUpperCase());
  }

  private void sendAlertMail(List<String> alerts, LocalDate date) {
    if (mailService == null) {
      logger.info("MailService not available – skipping wish price notification");
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
      logger.warn("Could not send wish price alert mail: {}", e.getMessage());
    }
  }
}
