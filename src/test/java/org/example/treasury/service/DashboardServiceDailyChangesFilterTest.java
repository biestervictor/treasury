package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.example.treasury.dto.DashboardDto;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.model.Display;
import org.example.treasury.model.SecretLair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that daily changes for sold Displays and SecretLairs are filtered
 * out of the Dashboard highlights.
 */
class DashboardServiceDailyChangesFilterTest {

  private DisplayService displayService;
  private SecretLairService secretLairService;
  private EdelmetallService edelmetallService;
  private ShoeService shoeService;
  private CardMarketPriceHistoryService priceHistoryService;
  private DashboardService service;

  @BeforeEach
  void setUp() {
    displayService = mock(DisplayService.class);
    secretLairService = mock(SecretLairService.class);
    edelmetallService = mock(EdelmetallService.class);
    shoeService = mock(ShoeService.class);
    priceHistoryService = mock(CardMarketPriceHistoryService.class);

    service = new DashboardService(
        displayService, secretLairService, edelmetallService,
        shoeService, priceHistoryService);

    // Minimal stubs — no shoes, no metals, no secret lairs by default
    when(shoeService.getAllShoes()).thenReturn(List.of());
    when(secretLairService.getAllSecretLairs()).thenReturn(List.of());
    when(edelmetallService.getDashboard()).thenReturn(
        new MetalDashboardDto(null, List.of(), List.of(), List.of(), 0, 0));
  }

  /**
   * Active display's daily change must appear in daily top gainers.
   */
  @Test
  void dailyGainers_activeDisplay_isIncluded() {
    Display active = activeDisplay("d1", "IKO", "DRAFT");
    when(displayService.getAllDisplays()).thenReturn(List.of(active));

    CardMarketPriceHistoryService.DailyChange change =
        new CardMarketPriceHistoryService.DailyChange(
            "IKO|DRAFT", "DISPLAY", LocalDate.now().minusDays(1), 40.0, 50.0);
    when(priceHistoryService.getDailyChanges()).thenReturn(List.of(change));

    DashboardDto dashboard = service.getDashboard();

    assertEquals(1, dashboard.dailyTopGainers().size());
    assertEquals("IKO DRAFT", dashboard.dailyTopGainers().get(0).name());
  }

  /**
   * Daily change for a sold Display (key not present in active displays)
   * must NOT appear in daily highlights.
   */
  @Test
  void dailyGainers_soldDisplay_isExcluded() {
    // No active displays → displayNameByKey is empty
    when(displayService.getAllDisplays()).thenReturn(List.of());

    CardMarketPriceHistoryService.DailyChange change =
        new CardMarketPriceHistoryService.DailyChange(
            "ZNR|DRAFT", "DISPLAY", LocalDate.now().minusDays(1), 30.0, 45.0);
    when(priceHistoryService.getDailyChanges()).thenReturn(List.of(change));

    DashboardDto dashboard = service.getDashboard();

    assertTrue(dashboard.dailyTopGainers().isEmpty(),
        "Sold display should not appear in daily gainers");
    assertTrue(dashboard.dailyTopLosers().isEmpty(),
        "Sold display should not appear in daily losers");
  }

  /**
   * Mix: one active and one sold display → only the active one appears.
   */
  @Test
  void dailyGainers_mixedDisplays_onlyActiveAppears() {
    Display active = activeDisplay("d1", "IKO", "DRAFT");
    when(displayService.getAllDisplays()).thenReturn(List.of(active));

    CardMarketPriceHistoryService.DailyChange activeChange =
        new CardMarketPriceHistoryService.DailyChange(
            "IKO|DRAFT", "DISPLAY", LocalDate.now().minusDays(1), 40.0, 50.0);
    CardMarketPriceHistoryService.DailyChange soldChange =
        new CardMarketPriceHistoryService.DailyChange(
            "ZNR|DRAFT", "DISPLAY", LocalDate.now().minusDays(1), 30.0, 45.0);
    when(priceHistoryService.getDailyChanges())
        .thenReturn(List.of(activeChange, soldChange));

    DashboardDto dashboard = service.getDashboard();

    assertEquals(1, dashboard.dailyTopGainers().size());
    assertEquals("IKO DRAFT", dashboard.dailyTopGainers().get(0).name());
  }

  /**
   * Daily change for a sold SecretLair must NOT appear (existing behaviour,
   * regression guard).
   */
  @Test
  void dailyGainers_soldSecretLair_isExcluded() {
    when(displayService.getAllDisplays()).thenReturn(List.of());

    CardMarketPriceHistoryService.DailyChange change =
        new CardMarketPriceHistoryService.DailyChange(
            "sl-sold-42", "SECRET_LAIR", LocalDate.now().minusDays(1), 20.0, 35.0);
    when(priceHistoryService.getDailyChanges()).thenReturn(List.of(change));

    DashboardDto dashboard = service.getDashboard();

    assertTrue(dashboard.dailyTopGainers().isEmpty(),
        "Sold SecretLair should not appear in daily gainers");
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private Display activeDisplay(String id, String setCode, String type) {
    Display d = new Display();
    d.setId(id);
    d.setSetCode(setCode);
    d.setType(type);
    d.setCurrentValue(50.0);
    d.setValueBought(40.0);
    d.setSold(false);
    return d;
  }
}
