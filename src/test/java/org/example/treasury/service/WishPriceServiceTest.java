package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.treasury.model.CardMarketPriceSnapshot;
import org.example.treasury.model.MagicSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link WishPriceService}: setWishPrice, removeWishPrice, getPriceHistory.
 */
class WishPriceServiceTest {

  private MagicSetService magicSetService;
  private DisplayPriceCollectorService displayPriceCollectorService;
  private CardMarketPriceHistoryService priceHistoryService;
  private WishPriceService service;

  @BeforeEach
  void setUp() {
    magicSetService = Mockito.mock(MagicSetService.class);
    displayPriceCollectorService = Mockito.mock(DisplayPriceCollectorService.class);
    priceHistoryService = Mockito.mock(CardMarketPriceHistoryService.class);
    service = new WishPriceService(magicSetService, displayPriceCollectorService, priceHistoryService);
  }

  @Test
  void setWishPrice_addsEntryToMap() {
    MagicSet set = MagicSet.builder().code("ZNR").name("Zendikar Rising").build();
    when(magicSetService.getMagicSetByCode("ZNR")).thenReturn(List.of(set));

    service.setWishPrice("ZNR", "DRAFT", 89.99);

    assertNotNull(set.getWishPrices());
    assertEquals(89.99, set.getWishPrices().get("DRAFT"));
    verify(magicSetService).saveMagicSet(set);
  }

  @Test
  void setWishPrice_updatesExistingEntry() {
    Map<String, Double> existing = new HashMap<>();
    existing.put("DRAFT", 79.99);
    MagicSet set = MagicSet.builder().code("ZNR").name("Zendikar Rising")
        .wishPrices(existing).build();
    when(magicSetService.getMagicSetByCode("ZNR")).thenReturn(List.of(set));

    service.setWishPrice("ZNR", "DRAFT", 89.99);

    assertEquals(89.99, set.getWishPrices().get("DRAFT"));
  }

  @Test
  void setWishPrice_withZeroPrice_removesEntry() {
    Map<String, Double> existing = new HashMap<>();
    existing.put("DRAFT", 79.99);
    MagicSet set = MagicSet.builder().code("ZNR").name("Zendikar Rising")
        .wishPrices(existing).build();
    when(magicSetService.getMagicSetByCode("ZNR")).thenReturn(List.of(set));

    service.setWishPrice("ZNR", "DRAFT", 0);

    assertNull(set.getWishPrices());
  }

  @Test
  void setWishPrice_unknownSetCode_doesNothing() {
    when(magicSetService.getMagicSetByCode("XXX")).thenReturn(List.of());

    service.setWishPrice("XXX", "DRAFT", 89.99);

    verify(magicSetService, never()).saveMagicSet(any());
  }

  @Test
  void removeWishPrice_removesEntry_andClearsMapWhenEmpty() {
    Map<String, Double> existing = new HashMap<>();
    existing.put("DRAFT", 89.99);
    MagicSet set = MagicSet.builder().code("ZNR").name("Zendikar Rising")
        .wishPrices(existing).build();
    when(magicSetService.getMagicSetByCode("ZNR")).thenReturn(List.of(set));

    service.removeWishPrice("ZNR", "DRAFT");

    assertNull(set.getWishPrices());
    verify(magicSetService).saveMagicSet(set);
  }

  @Test
  void removeWishPrice_preservesOtherEntries() {
    Map<String, Double> existing = new HashMap<>();
    existing.put("DRAFT", 89.99);
    existing.put("COLLECTOR", 145.00);
    MagicSet set = MagicSet.builder().code("ZNR").name("Zendikar Rising")
        .wishPrices(existing).build();
    when(magicSetService.getMagicSetByCode("ZNR")).thenReturn(List.of(set));

    service.removeWishPrice("ZNR", "DRAFT");

    assertNotNull(set.getWishPrices());
    assertNull(set.getWishPrices().get("DRAFT"));
    assertEquals(145.00, set.getWishPrices().get("COLLECTOR"));
  }

  @Test
  void getPriceHistory_queriesCorrectItemId() {
    CardMarketPriceSnapshot snap = CardMarketPriceSnapshot.builder()
        .itemId("ZNR|DRAFT").itemType("DISPLAY").price(89.99).build();
    when(priceHistoryService.getHistory("ZNR|DRAFT")).thenReturn(List.of(snap));

    List<CardMarketPriceSnapshot> result = service.getPriceHistory("ZNR", "DRAFT");

    assertEquals(1, result.size());
    assertEquals("ZNR|DRAFT", result.get(0).getItemId());
    verify(priceHistoryService).getHistory(eq("ZNR|DRAFT"));
  }

  @Test
  void getPriceHistory_normalizesToUpperCase() {
    when(priceHistoryService.getHistory("ZNR|COLLECTOR")).thenReturn(List.of());

    service.getPriceHistory("znr", "collector");

    verify(priceHistoryService).getHistory(eq("ZNR|COLLECTOR"));
  }
}
