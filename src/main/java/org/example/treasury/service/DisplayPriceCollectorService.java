package org.example.treasury.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.springframework.stereotype.Service;

@Service
public class DisplayPriceCollectorService extends PriceCollectorService {
  private final DisplayService displayService;

  public DisplayPriceCollectorService(DisplayService displayService) {
    this.displayService = displayService;
  }

  public void runScraper(Playwright playwright, Display display, boolean isLEgacy) {
    Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
        .setUserAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

    BrowserContext context = browser.newContext(contextOptions);
    String setCode = display.getSetCode();
    String setName = display.getName();
    String type = display.getType();

    List<Angebot> angebote= new ArrayList<>();
    String url = buildUrl(setCode, setName, type, isLEgacy);
    if(display.getLanguage().equals("EN")){
        url = url.replace("language=1,3", "language=1");
    }else {
        url = url.replace("language=1,3", "language=3");
    }
    display.setUrl(url);
    try {

      logger.info(
          "🛒 Günstigste Angebote pro Verkäufer von " + setCode + "/" + setName + "/" + type +
              ":\n");
       angebote=requestOffers(context,
          display.getUrl());
       display.setAngebotList(angebote);
       display.setCurrentValue(display.getRelevantPreis());

    } catch (Exception e) {
      logger.error("❌ Fehler beim Scraping: " + e.getMessage());
      logger.error(setCode + " " + type + " " + url);

    }finally {
      displayService.updateAngeboteBySetCodeAndType(display);
      browser.close();
    }
  }


  private String buildUrl(String setCode, String setName, String type, boolean isLegacy) {

    String base = "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/";
    if (setName == null || setName.isEmpty()) {
      setName = "Unbekanntes Set";
      logger.error("Beim Scrapper Set Name ist leer oder null, verwende 'Unbekanntes Set'");
    }
    if (isLegacy) {
      type = "legacy";
    }
    String namePart = setName.replace(" ", "-").replace("'", "").replace(":", "");
    String url = switch (type) {
      case "COLLECTOR" -> base + namePart + "-Collector-Booster-Box?sellerCountry=7&language=1,3";
      case "SET" -> base + namePart + "-Set-Booster-Box?sellerCountry=7&language=1,3";
      case "PLAY" -> base + namePart + "-Play-Booster-Box?sellerCountry=7&language=1,3";
      case "DRAFT" -> base + namePart + "-Draft-Booster-Box?sellerCountry=7&language=1,3";
      case "BUNDLE" ->
          "https://www.cardmarket.com/de/Magic/Products/Bundles-Fat-Packs/" + namePart +
              "-Fat-Pack-Bundle?sellerCountry=7&language=1,3";
      case "PRERELEASE" ->
          "https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/" + namePart +
              "-Prerelease-Pack?sellerCountry=7&language=1,3";
      default -> base + namePart + "-Booster-Box?sellerCountry=7&language=1,3";
    };

    return fixUrls(setCode, type, url);
  }

  private static final Map<String, String> SETCODE_URL_MAP = Map.ofEntries(
      Map.entry("2XM", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Double-Masters-2022-Draft-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("FIF", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-FINAL-FANTASY-Play-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("M21", " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2021-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("THB", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Theros-Beyond-Death-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("MAT", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/March-of-the-Machine-The-Aftermath-Epilogue-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("DGM", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Dragon-s-Maze-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("M20", " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2020-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("UNF", " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Unfinity-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("CMB2", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Mystery-Booster-Convention-Edition-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("MB2", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Mystery-Booster-2-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("WHO", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Universes-Beyond-Doctor-Who-Collector-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("FDN", " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Foundations-Play-Booster-Box?sellerCountry=7&language=1,3"),
      Map.entry("SPM", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Marvels-Spider-Man-Collector-Booster-Box"),
      Map.entry("TLA", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Avatar-The-Last-Airbender-Collector-Booster-Box"),
      Map.entry("TMT", "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Teenage-Mutant-Ninja-Turtles-Collector-Booster-Box")
  );

  private String fixUrls(String setCode, String types, String url) {
    // Spezialfall: BRO + PRERELEASE
    if (setCode.equals("BRO") && types.equals("PRERELEASE")) {
      return " https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/The-Brothers-War-Prerelease-Pack-Urzas-Iron-Alliance?sellerCountry=7&language=1,3";
    }
    // Standardfälle aus Map
    if (SETCODE_URL_MAP.containsKey(setCode)) {
      return SETCODE_URL_MAP.get(setCode);
    }

    return url;
  }
}