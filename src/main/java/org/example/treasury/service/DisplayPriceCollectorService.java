package org.example.treasury.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service

public class DisplayPriceCollectorService {

  private final DisplayService displayService;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  public DisplayPriceCollectorService(DisplayService displayService) {
    this.displayService = displayService;
  }

  public void runScraper(BrowserContext context, Display display, boolean isLEgacy) {

    String setCode = display.getSetCode();
    String setName = display.getName();
    String type = display.getType();

    String url = buildUrl(setCode, setName, type, isLEgacy);
    try {

      logger.info(
          "🛒 Günstigste Angebote pro Verkäufer von " + setCode + "/" + setName + "/" + type +
              ":\n");
      displayService.updateAngeboteBySetCodeAndType(setCode, type, requestOffers(context, url),
          url);
    } catch (Exception e) {
      logger.error("❌ Fehler beim Scraping: " + e.getMessage());
      logger.error(setCode + " " + type + " " + url);

    }
  }

  private List<Angebot> requestOffers(BrowserContext context, String url) {

    Page page = context.newPage();

    page.navigate(url, new Page.NavigateOptions().setTimeout(30000)); // Timeout setzen
    if (!page.url().contains("cardmarket.com")) {
      throw new RuntimeException("Seite nicht korrekt geladen: " + page.url());
    }
    page.waitForSelector(".table.article-table .article-row",
        new Page.WaitForSelectorOptions().setTimeout(30000));

    List<ElementHandle> rows = page.querySelectorAll(".table.article-table .article-row");
    Map<String, Angebot> angebote = new LinkedHashMap<>();
    int i = 1;
    for (ElementHandle row : rows) {
      ElementHandle nameEl = row.querySelector(".seller-info .seller-name a");
      if (nameEl == null) {
        continue;
      }
      String name = nameEl.innerText().trim();

      ElementHandle preisEl = row.querySelector(".price-container span.color-primary");
      if (preisEl == null) {
        preisEl = row.querySelector(".mobile-offer-container span.color-primary");
      }
      if (preisEl == null) {
        continue;
      }

      String preisText =
          preisEl.innerText().replace(".", "").replace(",", ".").replace("€", "").trim();
      double preis;
      try {
        preis = Double.parseDouble(preisText);
      } catch (NumberFormatException e) {
        continue;
      }

      ElementHandle mengeEl = row.querySelector(".amount-container .item-count");
      String menge = (mengeEl != null) ? mengeEl.innerText().trim() : "—";

      Angebot vorhandenes = angebote.get(name);

      if (vorhandenes == null || preis < vorhandenes.getPreis()) {

        angebote.put(name, new Angebot(name, preis, menge));
        i++;
      }
      if (i > 5) {
        break;
      }
    }


    List<Angebot> angebotList = new ArrayList<>();


    for (Map.Entry<String, Angebot> entry : angebote.entrySet()) {

      Angebot angebot = entry.getValue();
      logger.info(String.format("\nVerkäufer:     %s\nPreis:         %.2f €\nVerfügbarkeit: %s\n\n",
          angebot.getName(), angebot.getPreis(), angebot.getMenge()));
      angebotList.add(angebot);
    }


    return angebotList;


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

    if (setCode.equals("2xm")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Double-Masters-2022-Draft-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("fif")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-FINAL-FANTASY-Play-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("m21")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2021-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("bro") && types.equals("PRERELEASE")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Tournament-Packs/The-Brothers-War-Prerelease-Pack-Urzas-Iron-Alliance?sellerCountry=7&language=1,3";
    } else if (setCode.equals("thb")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Theros-Beyond-Death-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("mat")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/March-of-the-Machine-The-Aftermath-Epilogue-Booster-Box?sellerCountry=7&language=1,3";

    } else if (setCode.equals("dgm")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Dragon-s-Maze-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("m20")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Core-2020-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("unf")) {
      url =
          " https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Unfinity-Booster-Box?sellerCountry=7&language=1,3";
    } else if (setCode.equals("cmb2")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Mystery-Booster-Convention-Edition-Booster-Box?sellerCountry=7&language=1,3";
    }


    return url;
  }
}