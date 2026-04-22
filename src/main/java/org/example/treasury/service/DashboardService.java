package org.example.treasury.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

  /**
   * Konstruktor mit allen benötigten Services.
   *
   * @param displayService     Display-Service
   * @param secretLairService  SecretLair-Service
   * @param edelmetallService  Edelmetall-Service
   * @param shoeService        Schuh-Service
   */
  public DashboardService(
      DisplayService displayService,
      SecretLairService secretLairService,
      EdelmetallService edelmetallService,
      ShoeService shoeService) {
    this.displayService = displayService;
    this.secretLairService = secretLairService;
    this.edelmetallService = edelmetallService;
    this.shoeService = shoeService;
  }

  /**
   * Erstellt das aggregierte Dashboard-DTO.
   *
   * @return vollständiges DashboardDto mit allen Kategorien und Highlights
   */
  public DashboardDto getDashboard() {
    List<DashboardDto.ItemHighlight> allItems = new ArrayList<>();

    DashboardDto.CategorySummary displaySummary = buildDisplaySummary(allItems);
    DashboardDto.CategorySummary secretLairSummary = buildSecretLairSummary(allItems);
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

    return new DashboardDto(
        categories,
        totalInvested,
        totalCurrentValue,
        totalProfit,
        totalBelow,
        topGainers,
        topLosers,
        belowPurchaseItems);
  }

  private DashboardDto.CategorySummary buildDisplaySummary(
      List<DashboardDto.ItemHighlight> allItems) {
    List<Display> active = displayService.getAllDisplays().stream()
        .filter(d -> !d.isSold())
        .filter(d -> d.getCurrentValue() > 0)
        .toList();

    double invested = active.stream().mapToDouble(Display::getValueBought).sum();
    double current = active.stream().mapToDouble(Display::getCurrentValue).sum();

    for (Display d : active) {
      double profit = d.getCurrentValue() - d.getValueBought();
      String label = d.getSetCode() + " " + d.getType();
      allItems.add(new DashboardDto.ItemHighlight("MTG Display", label,
          d.getValueBought(), d.getCurrentValue(), profit, DISPLAY_LINK));
    }

    int below = (int) active.stream()
        .filter(d -> d.getCurrentValue() < d.getValueBought()).count();

    return new DashboardDto.CategorySummary(
        "MTG Displays", "primary", active.size(),
        invested, current, current - invested, below, DISPLAY_LINK);
  }

  private DashboardDto.CategorySummary buildSecretLairSummary(
      List<DashboardDto.ItemHighlight> allItems) {
    List<SecretLair> active = secretLairService.getAllSecretLairs().stream()
        .filter(s -> !s.isSold())
        .filter(s -> s.getCurrentValue() > 0)
        .toList();

    double invested = active.stream().mapToDouble(SecretLair::getValueBought).sum();
    double current = active.stream().mapToDouble(SecretLair::getCurrentValue).sum();

    for (SecretLair s : active) {
      double profit = s.getCurrentValue() - s.getValueBought();
      allItems.add(new DashboardDto.ItemHighlight("Secret Lair", s.getName(),
          s.getValueBought(), s.getCurrentValue(), profit, SECRET_LAIR_LINK));
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
      allItems.add(new DashboardDto.ItemHighlight("Edelmetall", v.getName(),
          itemInvested, v.getCurrentTotalValue(), v.getProfit(), METAL_LINK));
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
      allItems.add(new DashboardDto.ItemHighlight("Sneaker",
          s.getName() != null ? s.getName() : s.getTyp(),
          s.getValueBought(), currentVal, shoeProfit, SHOE_LINK));
    }

    int below = (int) active.stream()
        .filter(s -> s.getValueStockX() > 0 && s.getValueStockX() < s.getValueBought())
        .count();

    return new DashboardDto.CategorySummary(
        "Sneaker", "danger", active.size(),
        invested, current, current - invested, below, SHOE_LINK);
  }
}
