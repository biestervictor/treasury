package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import org.example.treasury.repository.MetalPriceSnapshotRepository;
import org.example.treasury.repository.MetalValuationSnapshotRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EdelmetallServiceImportDuplicatesTest {

  @Test
  void buildImportKey_shouldBeStableForSameValues() {
    var m1 = org.example.treasury.model.PreciousMetal.builder()
        .name(" Gold   Coin ")
        .year(2020)
        .quantity(2)
        .weightInGrams(28.3495)
        .type(org.example.treasury.model.PreciousMetalType.GOLD)
        .purchasePrice(100)
        .build();

    var m2 = org.example.treasury.model.PreciousMetal.builder()
        .name("gold coin")
        .year(2020)
        .quantity(2)
        .weightInGrams(28.34950001)
        .type(org.example.treasury.model.PreciousMetalType.GOLD)
        .purchasePrice(100.0000001)
        .build();

    assertThat(EdelmetallService.buildImportKey(m1))
        .isEqualTo(EdelmetallService.buildImportKey(m2));
  }

  @Test
  void import_shouldSkipExistingByImportKey() {
    PreciousMetalRepository preciousMetalRepository = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository snapshotRepository = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepository = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = () -> new MetalPriceClient.Prices(0, 0, Instant.EPOCH);

    Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);

    EdelmetallService service = new EdelmetallService(preciousMetalRepository, snapshotRepository, valuationRepository,
        client, clock);

    var existing = org.example.treasury.model.PreciousMetal.builder()
        .name("Gold Coin")
        .year(2020)
        .quantity(2)
        .weightInGrams(28.3495)
        .type(org.example.treasury.model.PreciousMetalType.GOLD)
        .purchasePrice(100)
        .importedAt(LocalDate.parse("2026-03-01"))
        .build();

    var newOne = org.example.treasury.model.PreciousMetal.builder()
        .name("Silver Coin")
        .year(2019)
        .quantity(3)
        .weightInGrams(28.3495)
        .type(org.example.treasury.model.PreciousMetalType.SILVER)
        .purchasePrice(20.26)
        .importedAt(LocalDate.parse("2026-03-01"))
        .build();

    String existingKey = EdelmetallService.buildImportKey(existing);
    String newKey = EdelmetallService.buildImportKey(newOne);
    assertThat(existingKey).isNotBlank();
    assertThat(newKey).isNotBlank();
    assertThat(newKey).isNotEqualTo(existingKey);

    Mockito.when(preciousMetalRepository.existsByImportKey(existingKey)).thenReturn(true);
    Mockito.when(preciousMetalRepository.existsByImportKey(newKey)).thenReturn(false);

    // Act
    int imported = service.importMetals(List.of(existing, newOne), LocalDate.parse("2026-03-01"));

    // Assert
    assertThat(imported).isEqualTo(1);
    Mockito.verify(preciousMetalRepository).existsByImportKey(existingKey);
    Mockito.verify(preciousMetalRepository).existsByImportKey(newKey);
    Mockito.verify(preciousMetalRepository).save(Mockito.argThat(m -> newKey.equals(m.getImportKey())));
    Mockito.verifyNoMoreInteractions(preciousMetalRepository);
    Mockito.verifyNoInteractions(snapshotRepository);
    Mockito.verifyNoInteractions(valuationRepository);
  }
}

