package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.repository.MetalPriceSnapshotRepository;
import org.example.treasury.repository.MetalValuationSnapshotRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.example.treasury.model.MetalValuationSnapshot;

class EdelmetallServiceDashboardDefaultPriceTest {

  @Test
  void dashboard_containsInitialDefaultPoint_whenNoSnapshotsExist() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository snapshotRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = Mockito.mock(MetalPriceClient.class);

    Instant fixedNow = Instant.parse("2026-01-01T10:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    when(snapshotRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());
    when(valuationRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());
    // ensureInitialValuationExistsOnce() speichert einen Snapshot, danach wird die Timeline aus findAll.. gebaut.
    when(valuationRepo.save(Mockito.any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(valuationRepo.findAllByOrderByTimestampAsc()).thenAnswer(inv -> List.of(MetalValuationSnapshot.builder()
        .timestamp(fixedNow)
        .items(List.of())
        .totalCurrentValue(0.0)
        .totalProfit(0.0)
        .build()));
    when(metalRepo.findAllByImportedAtIsNotNull()).thenReturn(List.of());

    EdelmetallService service = new EdelmetallService(metalRepo, snapshotRepo, valuationRepo, client, clock);

    MetalDashboardDto dto = service.getDashboard();

    assertThat(dto.currentPrices().timestamp()).isEqualTo(fixedNow);
    assertThat(dto.profitTimeline()).isNotEmpty();

    // Default-Preise wie in der Story: Silber 58,83 €/oz, Gold 3864,38 €/oz
    assertThat(dto.currentPrices().goldEurPerOunce()).isEqualTo(3864.38);
    assertThat(dto.currentPrices().silverEurPerOunce()).isEqualTo(58.83);
  }
}

