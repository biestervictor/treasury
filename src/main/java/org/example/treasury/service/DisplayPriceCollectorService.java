package org.example.treasury.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.treasury.dto.MailRequest;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Scrapes current Cardmarket prices for Display products.
 */
@Service
public class DisplayPriceCollectorService extends PriceCollectorService {

  /** Cardmarket username used for sell-monitoring. */
  public static final String VICTORBIESTER = "Victorbiester";

  private final DisplayService displayService;
  private final Optional<MailService> mailService;
  private final CardMarketPriceHistoryService priceHistoryService;

  @Value("${treasury.jobs.sell.notifyOnStartupSchedule:true}")
  private boolean notifyOnStartupSchedule;

  @Value("${treasury.jobs.sell.notifyOnStartupResult:true}")
  private boolean notifyOnStartupResult;

  @Value("${treasury.mail.startup.to:}")
  private String startupMailTo;

  /**
   * Constructor.
   *
   * @param displayService      the display service
   * @param mailService         optional mail service
   * @param priceHistoryService the price history service
   */
  public DisplayPriceCollectorService(DisplayService displayService,
                                      Optional<MailService> mailService,
                                      CardMarketPriceHistoryService priceHistoryService) {
    this.displayService = displayService;
    this.mailService = mailService;
    this.priceHistoryService = priceHistoryService;
  }

  /**
   * Runs the Cardmarket scraper for a single Display and saves a price history snapshot.
   *
   * @param playwright the Playwright instance
   * @param display    the display to scrape
   * @param isLEgacy   whether this set predates Zendikar Rising
   */
  public void runScraper(Playwright playwright, Display display, boolean isLEgacy) {
    Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
        .setUserAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

    BrowserContext context = browser.newContext(contextOptions);
    String setCode = display.getSetCode();
    String setName = display.getName();
    String type = display.getType();

    List<Angebot> angebote = new ArrayList<>();

    String url = buildUrl(setCode, setName, type, isLEgacy, display.getLanguage());
    display.setUrl(url);
    try {

      logger.debug(
          "Günstigste Angebote pro Verkäufer von " + setCode + "/" + setName + "/" + type);
      angebote = requestOffers(context, display.getUrl());

      display.setCurrentValue(display.getRelevantPreis());

      if (display.isSelling() && !display.getAngebotList().isEmpty() && !display.isSold()) {
        String cheapestSeller = display.getAngebotList().getFirst().getName();
        if (!VICTORBIESTER.equals(cheapestSeller)) {
          logger.info(
              "Verkauf aktiv, aber der günstigste Anbieter ist nicht " + VICTORBIESTER);
          if (notifyOnStartupSchedule) {
            sendStartupMail("Du wurdest unterboten",
                "Display " + display.getLanguage() + "//" + display.getSetCode()
                    + "//" + display.getType()
                    + "\nDer günstigste Anbieter ist aktuell: " + cheapestSeller
                    + " mit " + display.getAngebotList().getFirst().getPreis() + "€"
                    + "\n\nURL: " + display.getUrl());
          }
        }
        logger.info("Verkauf aktiv, günstigster Anbieter geprüft");
      }
      logger.info("Scraping erfolgreich: " + display.getName() + " " + display.getUrl());
    } catch (Exception e) {
      logger.error("Fehler beim Scraping: " + e.getMessage());
      logger.error(setCode + " " + type + " " + url);

    } finally {
      display.setAngebotList(angebote);
      displayService.updateAngeboteBySetCodeAndType(display);
      saveDisplaySnapshot(display);
      browser.close();
    }
  }

  /**
   * Saves a price history snapshot for the given display's product (setCode|type).
   *
   * @param display the display (angebotList must be set before calling this)
   */
  private void saveDisplaySnapshot(Display display) {
    Double price = display.getRelevantPreis();
    if (price != null && price > 0) {
      String key = display.getSetCode() + "|" + display.getType();
      priceHistoryService.saveSnapshot(key, "DISPLAY", price, LocalDate.now());
    }
  }

  private void sendStartupMail(String subject, String text) {
    if (startupMailTo == null || startupMailTo.isBlank()) {
      logger.debug("SellJob: treasury.mail.startup.to ist leer – keine Mail.");
      return;
    }

    mailService.ifPresent(service -> {
      try {
        service.send(new MailRequest(List.of(startupMailTo), subject, text));
      } catch (Exception e) {
        logger.warn("SellJob: konnte Mail nicht senden", e);
      }
    });
  }

  /**
   * Baut die Cardmarket-URL. Package-private, damit Unit-Tests ohne Playwright möglich sind.
   *
   * @param setCode   the set code
   * @param setName   the set name
   * @param type      the display type
   * @param isLegacy  whether this is a legacy set
   * @param language  the language code
   * @return the constructed Cardmarket URL
   */
  String buildUrl(String setCode, String setName, String type,
                  boolean isLegacy, String language) {
    String base = "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/";
    if (setName == null || setName.isEmpty()) {
      setName = "Unbekanntes Set";
      logger.error("Beim Scrapper Set Name ist leer oder null, verwende 'Unbekanntes Set'");
    }

    String namePart = setName.replace(" ", "-").replace("'", "").replace(":", "");
    String lang = toCardmarketLanguageParam(language);
    String query = "sellerCountry=7&language=" + lang;
    if (isLegacy) {
      type = "legacy";
    }
    String url = switch (type) {
      case "COLLECTOR" -> base + namePart + "-Collector-Booster-Box?" + query;
      case "SET" -> base + namePart + "-Set-Booster-Box?" + query;
      case "PLAY" -> base + namePart + "-Play-Booster-Box?" + query;
      case "DRAFT" -> base + namePart + "-Draft-Booster-Box?" + query;
      case "BUNDLE" ->
          "https://www.cardmarket.com/de/Magic/Products/Bundles-Fat-Packs/" + namePart
              + "-Fat-Pack-Bundle?" + query;
      case "PRERELEASE" ->
          "https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/" + namePart
              + "-Prerelease-Pack?" + query;
      case "COMMANDER" ->
          "https://www.cardmarket.com/de/Magic/Products/Commander-Decks/" + namePart
              + "-Commander-Deck?" + query;
      default -> base + namePart + "-Booster-Box?" + query;
    };

    return fixUrls(setCode, type, url, query);
  }

  private static String toCardmarketLanguageParam(String language) {
    if (language == null || language.isBlank()) {
      return "1,3";
    }
    if ("EN".equalsIgnoreCase(language.trim())) {
      return "1";
    }
    if ("DE".equalsIgnoreCase(language.trim())) {
      return "3";
    }
    return "1,3";
  }

  private static final Map<String, String> SETCODE_URL_MAP = Map.ofEntries(
      Map.entry("2XM",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Double-Masters-2022-Draft-Booster-Box?"),
      Map.entry("FIF",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Magic-The-Gathering-FINAL-FANTASY-Play-Booster-Box?"),
      Map.entry("M21",
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2021-Booster-Box?"),
      Map.entry("THB",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Theros-Beyond-Death-Booster-Box?"),
      Map.entry("MAT",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "March-of-the-Machine-The-Aftermath-Epilogue-Booster-Box?"),
      Map.entry("DGM",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Dragon-s-Maze-Booster-Box?"),
      Map.entry("M20",
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2020-Booster-Box?"),
      Map.entry("UNF",
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Unfinity-Booster-Box?"),
      Map.entry("CMB2",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Mystery-Booster-Convention-Edition-Booster-Box?"),
      Map.entry("MB2",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Mystery-Booster-2-Booster-Box?"),
      Map.entry("WHO",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Universes-Beyond-Doctor-Who-Collector-Booster-Box?"),
      Map.entry("FDN",
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Magic-The-Gathering-Foundations-Play-Booster-Box?"),
      Map.entry("SPM",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Magic-The-Gathering-Marvels-Spider-Man-Collector-Booster-Box?"),
      Map.entry("TLA",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Magic-The-Gathering-Avatar-The-Last-Airbender-Collector-Booster-Box?"),
      Map.entry("TMT",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Magic-The-Gathering-Teenage-Mutant-Ninja-Turtles-Collector-Booster-Box?"),
      Map.entry("ACR",
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/"
              + "Universes-Beyond-Assassins-Creed-Collector-Booster-Box?")
  );

  private String fixUrls(String setCode, String types, String url, String query) {
    if (setCode.equals("BRO") && types.equals("PRERELEASE")) {
      return " https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/"
          + "The-Brothers-War-Prerelease-Pack-Urzas-Iron-Alliance?" + query;
    }
    if (SETCODE_URL_MAP.containsKey(setCode)) {
      return SETCODE_URL_MAP.get(setCode) + query;
    }
    return url;
  }
}
