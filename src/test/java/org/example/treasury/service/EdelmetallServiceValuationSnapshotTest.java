package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.example.treasury.model.MetalPriceSnapshot;
import org.example.treasury.model.MetalValuationSnapshot;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;
import org.example.treasury.repository.MetalPriceSnapshotRepository;
import org.example.treasury.repository.MetalValuationSnapshotRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EdelmetallServiceValuationSnapshotTest {

  @Test
  void updatePricesAndStoreValuation_storesPerCoinValuation_usingTroyOunceConversion() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = Mockito.mock(MetalPriceClient.class);

    Instant ts = Instant.parse("2026-03-26T00:00:00Z");
    Clock clock = Clock.fixed(ts, ZoneOffset.UTC);

    PreciousMetal coin = PreciousMetal.builder()
        .id("m1")
        .name("Test Coin")
        .type(PreciousMetalType.GOLD)
        .weightInGrams(31.1034768) // 1 troy oz
        .quantity(2)
        .purchasePrice(100.0)
        .build();

    when(metalRepo.findAllByImportedAtIsNotNull()).thenReturn(List.of(coin));

    when(priceRepo.save(any(MetalPriceSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(valuationRepo.save(any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(valuationRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());

    when(client.fetchCurrentPrices()).thenReturn(new MetalPriceClient.Prices(200.0, 10.0, ts));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, client, clock);

    MetalValuationSnapshot stored = service.updatePricesAndStoreValuation();

    assertThat(stored.getTimestamp()).isEqualTo(ts);
    assertThat(stored.getItems()).hasSize(1);

    MetalValuationSnapshot.ItemValuation item = stored.getItems().getFirst();
    // 1 oz * 200 USD/oz = 200 USD unit value
    assertThat(item.getCurrentUnitValue()).isEqualTo(200.0);
    assertThat(item.getCurrentTotalValue()).isEqualTo(400.0);
    assertThat(item.getProfit()).isEqualTo(2 * (200.0 - 100.0));

    // Zusätzlich: es wird wirklich persistiert
    ArgumentCaptor<MetalValuationSnapshot> cap = ArgumentCaptor.forClass(MetalValuationSnapshot.class);
    verify(valuationRepo).save(cap.capture());
    assertThat(cap.getValue().getItems()).hasSize(1);
  }
}

