package org.example.treasury.dto;

import java.time.Instant;
import java.util.List;
import org.example.treasury.model.MetalValuationSnapshot;

public record MetalDashboardDto(
    PriceDto currentPrices,
    List<ProfitPointDto> profitTimeline,
    List<MetalValuationSnapshot.ItemValuation> latestValuations,
    List<MarketValuePointDto> marketValueTimeline,
    double currentProfitTotal,
    double currentMarketValueTotal
) {

  public record PriceDto(double goldEurPerOunce, double silverEurPerOunce, Instant timestamp) {
  }

  public record ProfitPointDto(Instant timestamp, double profitTotal) {
  }

  public record MarketValuePointDto(Instant timestamp, double marketValueTotal) {
  }
}

