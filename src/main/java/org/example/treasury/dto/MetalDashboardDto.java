package org.example.treasury.dto;

import java.time.Instant;
import java.util.List;
import org.example.treasury.model.MetalValuationSnapshot;

public record MetalDashboardDto(
    PriceDto currentPrices,
    List<ProfitPointDto> profitTimeline,
    List<MetalValuationSnapshot.ItemValuation> latestValuations,
    List<MarketValuePointDto> marketValueTimeline,
    List<SpotValuePointDto> spotValueTimeline,
    double currentProfitTotal,
    double currentMarketValueTotal,
    double currentSpotValueTotal
) {

  public record PriceDto(double goldEurPerOunce, double silverEurPerOunce, Instant timestamp) {
  }

  public record ProfitPointDto(Instant timestamp, double profitTotal) {
  }

  public record MarketValuePointDto(Instant timestamp, double marketValueTotal) {
  }

  public record SpotValuePointDto(Instant timestamp, double spotValueTotal) {
  }
}

