package org.example.treasury.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
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

  private String fixUrls(String setCode, String types, String url) {

    if (setCode.equals("2XM")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Double-Masters-2022-Draft-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("FIF")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-FINAL-FANTASY-Play-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("M21")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2021-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("BRO") && types.equals("PRERELEASE")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/The-Brothers-War-Prerelease-Pack-Urzas-Iron-Alliance?sellerCountry=7&language=1,3";
    } else if (setCode.equals("THB")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Theros-Beyond-Death-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("MAT")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/March-of-the-Machine-The-Aftermath-Epilogue-Booster-Box?sellerCountry=7&language=1,3";

    } else if (setCode.equals("DGM")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Dragon-s-Maze-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("M20")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2020-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("UNF")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Unfinity-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("CMB2")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Mystery-Booster-Convention-Edition-Booster-Box?sellerCountry=7&language=1,3";
    }else if (setCode.equals("MB2")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Mystery-Booster-2-Booster-Box?sellerCountry=7&language=1,3";
    }else if (setCode.equals("WHO")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Universes-Beyond-Doctor-Who-Collector-Booster-Box?sellerCountry=7&language=1,3";
    }else if (setCode.equals("FDN")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Foundations-Play-Booster-Box?sellerCountry=7&language=1,3";
    }
    else if (setCode.equals("ACR")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Universes-Beyond-Assassins-Creed-Collector-Booster-Box";
    }






    return url;
  }
}