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
import org.example.treasury.model.SecretLair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;




public abstract class PriceCollectorService {


  Logger logger = LoggerFactory.getLogger(this.getClass());

  protected List<Angebot> requestOffers(BrowserContext context, String url) {

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

}