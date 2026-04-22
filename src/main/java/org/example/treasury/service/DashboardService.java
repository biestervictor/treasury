package org.example.treasury.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.treasury.dto.DashboardDto;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.model.Display;
import org.example.treasury.model.MetalValuationSnapshot.ItemValuation;
import org.example.treasury.model.SecretLair;
import org.example.treasury.model.Shoe;
import org.springframework.stereotype.Service;

/**
 * DashboardService aggregiert Daten aller Asset-Kategorien
 * (Displays, SecretLair, Edelmetalle, Schuhe) für das globale Dashboard.
 */
@Service
public class DashboardService {

  private static final int TOP_N = 40;
  private static final int BELOW_PURCHASE_LIMIT = 40;
  private static final String DISPLAY_LINK = "/api/display/list";
  private static final String SECRET_LAIR_LINK = "/api/secretlair/insert";
  private static final String METAL_LINK = "/api/edelmetall/dashboard/view";
  private static final String SHOE_LINK = "/api/shoe/list";

  private final DisplayService displayService;
  private final SecretLairService secretLairService;
  private final EdelmetallService edelmetallService;
  private final ShoeService shoeService;
  private final CardMarketPriceHistoryService priceHistoryService;

  /**
   * Konstruktor mit allen benötigten Services.
   *
   * @param displayService      Display-Service
   * @param secretLairService   SecretLair-Service
   * @param edelmetallService   Edelmetall-Service
   * @param shoeService         Schuh-Service
   * @param priceHistoryService Preishistorie-Service
   */
  public DashboardService(
      DisplayService displayService,
      SecretLairService secretLairService,
      EdelmetallService edelmetallService,
      ShoeService shoeService,
      CardMarketPriceHistoryService priceHistoryService) {
    this.displayService = displayService;
    this.secretLairService = secretLairService;
    this.edelmetallService = edelmetallService;
    this.shoeService = shoeService;
    this.priceHistoryService = priceHistoryService;
  }

  /**
   * Erstellt das aggregierte Dashboard-DTO.
   *
   * @return vollständiges DashboardDto mit allen Kategorien und Highlights
   */
  public DashboardDto getDashboard() {
    List<DashboardDto.ItemHighlight> allItems = new ArrayList<>();

    // Fetch active items once — reused for summaries and daily highlights
    List<Display> activeDisplays = displayService.getAllDisplays().stream()
        .filter(d -> !d.isSold())
        .filter(d -> d.getCurrentValue() > 0)
        .toList();
    List<SecretLair> activeSecretLairs = secretLairService.getAllSecretLairs().stream()
        .filter(s -> !s.isSold())
        .filter(s -> s.getCurrentValue() > 0)
        .toList();

    // One history URL per setCode|type key (first matching display ID is sufficient)
    Map<String, String> displayHistUrlByKey = new LinkedHashMap<>();
    for (Display d : activeDisplays) {
      displayHistUrlByKey.putIfAbsent(
          d.getSetCode() + "|" + d.getType(),
          "/api/display/" + d.getId() + "/history");
    }

    DashboardDto.CategorySummary displaySummary =
        buildDisplaySummary(allItems, activeDisplays, displayHistUrlByKey);
    DashboardDto.CategorySummary secretLairSummary =
        buildSecretLairSummary(allItems, activeSecretLairs);
    DashboardDto.CategorySummary metalSummary = buildMetalSummary(allItems);
    DashboardDto.CategorySummary shoeSummary = buildShoeSummary(allItems);

    List<DashboardDto.CategorySummary> categories = List.of(
        displaySummary, secretLairSummary, metalSummary, shoeSummary);

    double totalInvested = categories.stream()
        .mapToDouble(DashboardDto.CategorySummary::totalInvested).sum();
    double totalCurrentValue = categories.stream()
        .mapToDouble(DashboardDto.CategorySummary::currentValue).sum();
    double totalProfit = totalCurrentValue - totalInvested;
    int totalBelow = categories.stream()
        .mapToInt(DashboardDto.CategorySummary::belowPurchasePriceCount).sum();

    List<DashboardDto.ItemHighlight> topGainers = allItems.stream()
        .filter(i -> i.profit() > 0)
        .sorted(Comparator.comparingDouble(DashboardDto.ItemHighlight::profit).reversed())
        .limit(TOP_N)
        .toList();

    List<DashboardDto.ItemHighlight> topLosers = allItems.stream()
        .filter(i -> i.profit() < 0)
        .sorted(Comparator.comparingDouble(DashboardDto.ItemHighlight::profit))
        .limit(TOP_N)
        .toList();

    List<DashboardDto.ItemHighlight> belowPurchaseItems = allItems.stream()
        .filter(i -> i.profit() < 0)
        .sorted(Comparator.comparingDouble(DashboardDto.ItemHighlight::profit))
        .limit(BELOW_PURCHASE_LIMIT)
        .toList();

    // Daily highlight lookups
    Map<String, String> displayNameByKey = new LinkedHashMap<>();
    for (Display d : activeDisplays) {
      displayNameByKey.putIfAbsent(
          d.getSetCode() + "|" + d.getType(),
          d.getSetCode() + " " + d.getType());
    }
    Map<String, SecretLair> slById = activeSecretLairs.stream()
        .collect(Collectors.toMap(SecretLair::getId, s -> s, (a, b) -> a));

    List<CardMarketPriceHistoryService.DailyChange> dailyChanges =
        priceHistoryService.getDailyChanges();

    List<DashboardDto.ItemHighlight> dailyTopGainers =
        buildDailyHighlights(dailyChanges, displayNameByKey, displayHistUrlByKey, slById, true);
    List<DashboardDto.ItemHighlight> dailyTopLosers =
        buildDailyHighlights(dailyChanges, displayNameByKey, displayHistUrlByKey, slById, false);

    return new DashboardDto(
        categories,
        totalInvested,
        totalCurrentValue,
        totalProfit,
        totalBelow,
        topGainers,
        topLosers,
        belowPurchaseItems,
        dailyTopGainers,
        dailyTopLosers);
  }

  private DashboardDto.CategorySummary buildDisplaySummary(
      List<DashboardDto.ItemHighlight> allItems,
      List<Display> active,
      Map<String, String> histUrlByKey) {

    double invested = active.stream().mapToDouble(Display::getValueBought).sum();
    double current = active.stream().mapToDouble(Display::getCurrentValue).sum();

    for (Display d : active) {
      double profit = d.getCurrentValue() - d.getValueBought();
      String label = d.getSetCode() + " " + d.getType();
      String key = d.getSetCode() + "|" + d.getType();
      allItems.add(new DashboardDto.ItemHighlight(
          "MTG Display", label,
          d.getValueBought(), d.getCurrentValue(), profit,
          DISPLAY_LINK, histUrlByKey.get(key)));
    }

    int below = (int) active.stream()
        .filter(d -> d.getCurrentValue() < d.getValueBought()).count();

    return new DashboardDto.CategorySummary(
        "MTG Displays", "primary", active.size(),
        invested, current, current - invested, below, DISPLAY_LINK);
  }

  private DashboardDto.CategorySummary buildSecretLairSummary(
      List<DashboardDto.ItemHighlight> allItems,
      List<SecretLair> active) {

    double invested = active.stream().mapToDouble(SecretLair::getValueBought).sum();
    double current = active.stream().mapToDouble(SecretLair::getCurrentValue).sum();

    for (SecretLair s : active) {
      double profit = s.getCurrentValue() - s.getValueBought();
      allItems.add(new DashboardDto.ItemHighlight(
          "Secret Lair", s.getName(),
          s.getValueBought(), s.getCurrentValue(), profit,
          SECRET_LAIR_LINK, "/api/secretlair/" + s.getId() + "/history"));
    }

    int below = (int) active.stream()
        .filter(s -> s.getCurrentValue() < s.getValueBought()).count();

    return new DashboardDto.CategorySummary(
        "Secret Lair", "success", active.size(),
        invested, current, current - invested, below, SECRET_LAIR_LINK);
  }

  private DashboardDto.CategorySummary buildMetalSummary(
      List<DashboardDto.ItemHighlight> allItems) {
    MetalDashboardDto metalDashboard = edelmetallService.getDashboard();
    List<ItemValuation> valuations = metalDashboard.latestValuations();

    double invested = valuations.stream()
        .mapToDouble(v -> v.getPurchasePrice() * v.getQuantity()).sum();
    double current = metalDashboard.currentMarketValueTotal();
    double profit = metalDashboard.currentProfitTotal();

    for (ItemValuation v : valuations) {
      double itemInvested = v.getPurchasePrice() * v.getQuantity();
      allItems.add(new DashboardDto.ItemHighlight(
          "Edelmetall", v.getName(),
          itemInvested, v.getCurrentTotalValue(), v.getProfit(),
          METAL_LINK, null));
    }

    int below = (int) valuations.stream()
        .filter(v -> v.getProfit() < 0).count();

    return new DashboardDto.CategorySummary(
        "Edelmetalle", "warning", valuations.size(),
        invested, current, profit, below, METAL_LINK);
  }

  private DashboardDto.CategorySummary buildShoeSummary(
      List<DashboardDto.ItemHighlight> allItems) {
    List<Shoe> active = shoeService.getAllShoes().stream()
        .filter(s -> s.getValueSold() == 0 || s.getValueSold() == 0.0)
        .toList();

    double invested = active.stream().mapToDouble(Shoe::getValueBought).sum();
    double current = active.stream().mapToDouble(s -> {
      double stockX = s.getValueStockX();
      return stockX > 0 ? stockX : s.getValueBought();
    }).sum();

    for (Shoe s : active) {
      double stockX = s.getValueStockX();
      double currentVal = stockX > 0 ? stockX : s.getValueBought();
      double shoeProfit = currentVal - s.getValueBought();
      allItems.add(new DashboardDto.ItemHighlight(
          "Sneaker",
          s.getName() != null ? s.getName() : s.getTyp(),
          s.getValueBought(), currentVal, shoeProfit,
          SHOE_LINK, null));
    }

    int below = (int) active.stream()
        .filter(s -> s.getValueStockX() > 0 && s.getValueStockX() < s.getValueBought())
        .count();

    return new DashboardDto.CategorySummary(
        "Sneaker", "danger", active.size(),
        invested, current, current - invested, below, SHOE_LINK);
  }

  private List<DashboardDto.ItemHighlight> buildDailyHighlights(
      List<CardMarketPriceHistoryService.DailyChange> changes,
      Map<String, String> displayNameByKey,
      Map<String, String> displayHistUrlByKey,
      Map<String, SecretLair> slById,
      boolean gainers) {

    Comparator<CardMarketPriceHistoryService.DailyChange> comp = gainers
        ? Comparator.comparingDouble(
            CardMarketPriceHistoryService.DailyChange::absoluteChange).reversed()
        : Comparator.comparingDouble(
            CardMarketPriceHistoryService.DailyChange::absoluteChange);

    return changes.stream()
        .filter(c -> gainers ? c.absoluteChange() > 0 : c.absoluteChange() < 0)
        .sorted(comp)
        .limit(TOP_N)
        .map(c -> toHighlight(c, displayNameByKey, displayHistUrlByKey, slById))
        .filter(Objects::nonNull)
        .toList();
  }

  private DashboardDto.ItemHighlight toHighlight(
      CardMarketPriceHistoryService.DailyChange c,
      Map<String, String> displayNameByKey,
      Map<String, String> displayHistUrlByKey,
      Map<String, SecretLair> slById) {

    if ("DISPLAY".equals(c.itemType())) {
      String name = displayNameByKey.getOrDefault(c.itemId(), c.itemId());
      String histUrl = displayHistUrlByKey.get(c.itemId());
      return new DashboardDto.ItemHighlight(
          "MTG Display", name,
          c.prevPrice(), c.currentPrice(), c.absoluteChange(),
          DISPLAY_LINK, histUrl);
    }
    if ("SECRET_LAIR".equals(c.itemType())) {
      SecretLair sl = slById.get(c.itemId());
      if (sl == null) {
        return null;
      }
      return new DashboardDto.ItemHighlight(
          "Secret Lair", sl.getName(),
          c.prevPrice(), c.currentPrice(), c.absoluteChange(),
          SECRET_LAIR_LINK, "/api/secretlair/" + c.itemId() + "/history");
    }
    return null;
  }
}
