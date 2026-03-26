package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.example.treasury.dto.ManualMetalPricesRequest;
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

class EdelmetallServiceManualPricesPersistTest {

  @Test
  void storeManualPricesAndValuation_persistsSnapshot_andAcceptsComma() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = Mockito.mock(MetalPriceClient.class);

    Instant now = Instant.parse("2026-03-26T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    PreciousMetal coin = PreciousMetal.builder()
        .id("m1")
        .name("Test")
        .type(PreciousMetalType.GOLD)
        .weightInGrams(31.1034768)
        .quantity(1)
        .purchasePrice(100.0)
        .importedAt(java.time.LocalDate.of(2026, 3, 26))
        .build();

    when(metalRepo.findAllByImportedAtIsNotNull()).thenReturn(List.of(coin));
    when(priceRepo.save(any(MetalPriceSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(valuationRepo.save(any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, client, clock);

    service.storeManualPricesAndValuation(new ManualMetalPricesRequest("3864,38", "58,83"));

    ArgumentCaptor<MetalPriceSnapshot> cap = ArgumentCaptor.forClass(MetalPriceSnapshot.class);
    verify(priceRepo).save(cap.capture());
    assertThat(cap.getValue().getGoldPriceEurPerOunce()).isEqualTo(3864.38);
    assertThat(cap.getValue().getSilverPriceEurPerOunce()).isEqualTo(58.83);

    verify(valuationRepo).save(any(MetalValuationSnapshot.class));
  }
}

