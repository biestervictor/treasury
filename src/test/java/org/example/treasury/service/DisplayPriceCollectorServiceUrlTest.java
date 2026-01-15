package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayPriceCollectorServiceUrlTest {

  @Test
  void buildUrl_khmKaldheimDraft_languageEN() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null);

    String actual = service.buildUrl("KHM", "Kaldheim", "DRAFT", true, "EN");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Kaldheim-Draft-Booster-Box?sellerCountry=7&language=1",
        actual);
  }

  @Test
  void buildUrl_tlaAvatarCollector_languageEN() {
    DisplayPriceCollectorService service = new DisplayPriceCollectorService(null);

    String actual = service.buildUrl("TLA", "Avatar: The last Airbender", "COLLECTOR", true, "EN");

    assertEquals(
        "https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/Magic-The-Gathering-Avatar-The-Last-Airbender-Collector-Booster-Box?sellerCountry=7&language=1",
        actual);
  }


}
