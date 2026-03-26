package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.example.treasury.model.MetalPriceSnapshot;
import org.example.treasury.repository.MetalPriceSnapshotRepository;
import org.example.treasury.repository.MetalValuationSnapshotRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EdelmetallServiceUpdatePricesTest {

  @Test
  void updatePrices_shouldStoreSnapshot() {
    PreciousMetalRepository preciousMetalRepository = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository snapshotRepository = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepository = Mockito.mock(MetalValuationSnapshotRepository.class);

    MetalPriceClient client = () -> new MetalPriceClient.Prices(2000.0, 25.0, Instant.parse("2026-03-01T00:00:00Z"));

    Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);

    EdelmetallService service = new EdelmetallService(preciousMetalRepository, snapshotRepository, valuationRepository,
        client, clock);

    Mockito.when(snapshotRepository.save(Mockito.any(MetalPriceSnapshot.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MetalPriceSnapshot saved = service.updatePrices();

    assertThat(saved.getTimestamp()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    assertThat(saved.getGoldPriceEurPerOunce()).isEqualTo(2000.0);
    assertThat(saved.getSilverPriceEurPerOunce()).isEqualTo(25.0);

    Mockito.verify(snapshotRepository).save(Mockito.any(MetalPriceSnapshot.class));
    Mockito.verifyNoMoreInteractions(snapshotRepository);
    Mockito.verifyNoMoreInteractions(preciousMetalRepository);
    Mockito.verifyNoMoreInteractions(valuationRepository);
  }
}

