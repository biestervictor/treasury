package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.Mockito;

class EdelmetallServiceUpdatePricesFallbackTest {

  @Test
  void updatePricesAndStoreValuation_doesNotPersist_whenClientFails_evenIfSnapshotExists() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);

    MetalPriceClient failingClient = () -> {
      throw new IllegalStateException("boom");
    };

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

    MetalPriceSnapshot last = MetalPriceSnapshot.builder()
        .timestamp(Instant.parse("2026-03-25T00:00:00Z"))
        .goldPriceEurPerOunce(200.0)
        .silverPriceEurPerOunce(10.0)
        .build();

    when(priceRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.of(last));
    when(valuationRepo.save(any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, failingClient, clock);

    assertThatThrownBy(service::updatePricesAndStoreValuation)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("boom");

    verify(valuationRepo, never()).save(any(MetalValuationSnapshot.class));
  }

  @Test
  void updatePricesAndStoreValuation_doesNotPersist_whenClientFails_andNoSnapshotExists() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);

    MetalPriceClient failingClient = () -> {
      throw new IllegalStateException("boom");
    };

    Instant now = Instant.parse("2026-03-26T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    PreciousMetal coin = PreciousMetal.builder()
        .id("m1")
        .name("Test")
        .type(PreciousMetalType.SILVER)
        .weightInGrams(31.1034768)
        .quantity(1)
        .purchasePrice(1.0)
        .importedAt(java.time.LocalDate.of(2026, 3, 26))
        .build();

    when(metalRepo.findAllByImportedAtIsNotNull()).thenReturn(List.of(coin));

    when(priceRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());
    when(valuationRepo.save(any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, failingClient, clock);

    assertThatThrownBy(service::updatePricesAndStoreValuation)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("boom");

    verify(valuationRepo, never()).save(any(MetalValuationSnapshot.class));
  }
}

