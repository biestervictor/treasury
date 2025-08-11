package org.example.treasury.service;

import com.microsoft.playwright.BrowserContext;
import java.time.LocalDate;
import java.util.List;
import org.example.treasury.model.SecretLair;
import org.springframework.stereotype.Service;

@Service
public class SecretLairPriceCollectorService extends PriceCollectorService {
  private final SecretLairService secretLairService;

  public SecretLairPriceCollectorService(SecretLairService secretLairService) {
    this.secretLairService = secretLairService;

  }

  public void runScraper(BrowserContext context, List<SecretLair> secretLairList) {

    for (SecretLair secretLair : secretLairList) {
      try {
        logger.info("🛒 Günstigste Angebote pro Verkäufer von " + secretLair.getName());

        secretLair.setUrl(buildUrl(secretLair));
        secretLair.setAngebotList( requestOffers(context, secretLair.getUrl()));
        secretLair.setUpdatedAt(LocalDate.now());
        secretLair.setCurrentValue(secretLair.getRelevantPreis());


        logger.info("✅ Scraping erfolgreich: " + secretLair.getName() + " " + secretLair.getUrl());

      } catch (Exception e) {
        logger.error("❌ Fehler beim Scraping: " + e.getMessage());
        logger.error(secretLair.getName() + " " + secretLair.getUrl());

      }finally {
        secretLairService.updateSecretLair(secretLair);
      }
    }

  }


  private String buildUrl(SecretLair secretLair) {

    String base = "https://www.cardmarket.com/de/Magic/Products/Sets/";
    String name = secretLair.getName();
    name = name.replace("&","").replace(" ", "-").replace("'", "").replace(":", "").replace("\"","").replace("Secret-Lair-Drop-Series-","");
    if (secretLair.isDeck()) {
      base = base.replace("Sets", "Preconstructed-Decks");
    } else {
      base += "Secret-Lair-Drop-Series-";
    }
    if (name == null || name.isEmpty()) {

      logger.error("Beim Scrapper Set Name ist leer oder null, verwende 'Unbekanntes Set'");
    }


    String url = base + name;
    url += "?sellerCountry=7&language=1";
    if (secretLair.isFoil()) {
      url += "&isFoil=Y";
    }

    return fixUrls(name, url);

  }

  private String fixUrls(String name, String url) {

    if (name.toLowerCase().contains("pride")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Sets/Secret-Lair-Drop-Pride-Across-the-Multiverse?sellerCountry=7&language=1&isFoil=N";
    } else if (name.toLowerCase().contains("barcelona") && name.toLowerCase().contains("rats")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Singles/DCI-Promos/Relentless-Rats-V2?sellerCountry=7";
    }else if (name.toLowerCase().contains("chicago") ) {
      if( name.toLowerCase().contains("serra")) {
        url =
            "https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series/Serra-the-Benevolent?sellerCountry=7&language=1";
      }else if( name.toLowerCase().contains("sliver")) {
        url =
            "https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series/The-First-Sliver?sellerCountry=7&language=1";
      }else if( name.toLowerCase().contains("ponder")) {
        url =
            "https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series/Ponder-V2?sellerCountry=7&language=1";
      }

    }
    else if (name.toLowerCase().contains("vegas") ) {
      if( name.toLowerCase().contains("rats")) {
        url =
            "https://www.cardmarket.com/de/Magic/Products/Singles/MagicCon-Products/Relentless-Rats?sellerCountry=7";
      }else if( name.toLowerCase().contains("sliver")) {
        url =
            "https://www.cardmarket.com/de/Magic/Products/Singles/DCI-Promos/Sliver-Hive?sellerCountry=7&language=1";
      }else if( name.toLowerCase().contains("ponder")) {
        url =
            " https://www.cardmarket.com/de/Magic/Products/Singles/DCI-Promos/Ponder?sellerCountry=7&language=1";
      }

    }
    else if (name.toLowerCase().contains("scarab")) {
      url =
          "https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series-December-Superdrop-2022/The-Scarab-God?sellerCountry=7";
    }else if(name.toLowerCase().contains("ways")){
      url="https://www.cardmarket.com/de/Magic/Products/Preconstructed-Decks/Secret-Lair-Commander-Deck-20-Ways-to-Win-Deck?sellerCountry=7";
    }else if(name.toLowerCase().contains("creative")){
      url="https://www.cardmarket.com/de/Magic/Products/Preconstructed-Decks/Commander-Modern-Horizons-3-Creative-Energy-Commander-Deck-Collectors-Edition?sellerCountry=7";
    }else if(name.toLowerCase().contains("deadeye")){
      url=" https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series/Deadeye-Navigator?sellerCountry=7&language=1";
    }else if(name.toLowerCase().contains("sol")){
      url=" https://www.cardmarket.com/de/Magic/Products/Singles/Secret-Lair-Drop-Series/Sol-Ring-V2?sellerCountry=7&language=1";
    }





System.out.println(url);
    return url;
  }
}