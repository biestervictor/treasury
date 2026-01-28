package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DisplayPriceCollectorServiceUrlTest {

  @Test
  void buildUrl_soiShadowsOverInnerstrafDraft_languageDE() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null, Optional.empty());

    String actual = service.buildUrl("SOI", "Shadows over Innistrad", "DRAFT", true, "DE");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Shadows-over-Innistrad-Booster-Box?sellerCountry=7&language=3",
        actual);
  }

  @Test
  void buildUrl_khmKaldheimDraft_languageEN() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null, Optional.empty());

    String actual = service.buildUrl("KHM", "Kaldheim", "DRAFT", false, "EN");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Kaldheim-Draft-Booster-Box?sellerCountry=7&language=1",
        actual);
  }

  @Test
  void buildUrl_khmKaldheimSet_languageEN() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null, Optional.empty());

    String actual = service.buildUrl("KHM", "Kaldheim", "SET", false, "DE");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Kaldheim-Set-Booster-Box?sellerCountry=7&language=3",
        actual);
  }

  @Test
  void buildUrl_tlaAvatarCollector_languageEN() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null, Optional.empty());

    String actual = service.buildUrl("TLA", "Avatar: The last Airbender", "COLLECTOR", true, "EN");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Avatar-The-Last-Airbender-Collector-Booster-Box?sellerCountry=7&language=1",
        actual);
  }
}
