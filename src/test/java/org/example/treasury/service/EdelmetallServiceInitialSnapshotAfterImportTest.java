package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EdelmetallServiceInitialSnapshotAfterImportTest {

  @Test
  void import_createsInitialPriceSnapshotAndValuationOnce_whenNoPriceSnapshotExists() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = Mockito.mock(MetalPriceClient.class);

    Instant now = Instant.parse("2026-03-26T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    when(priceRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.empty());
    when(priceRepo.save(any(MetalPriceSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(valuationRepo.save(any(MetalValuationSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    PreciousMetal coin = PreciousMetal.builder()
        .id("m1")
        .name("Test Coin")
        .type(PreciousMetalType.GOLD)
        .weightInGrams(31.1034768)
        .quantity(1)
        .purchasePrice(100.0)
        .importedAt(java.time.LocalDate.of(2026, 3, 26))
        .build();
    when(metalRepo.findAllByImportedAtIsNotNull()).thenReturn(List.of(coin));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, client, clock);

    // Wir testen nur die After-Import Nebenwirkung über Reflection-unabhängigem Trigger:
    // importMetals ist package-private/testbar und ruft ensure... nicht.
    // Daher rufen wir importEdelmetallFromCSV() hier nicht (Classpath-Resource), sondern direkt die Methode
    // über einen simulierten Import: wir lösen die Snapshot-Erstellung aus, indem wir CSV-Import anstoßen:
    // -> hierfür genügt es, die private Methode indirekt über importEdelmetallFromCSV zu testen wäre zu schwer.
    // Also: wir testen die Snapshot-Logik über getDashboard(), die initiale Valuation anlegt,
    // plus zusätzlich stellen wir sicher, dass PriceSnapshot nach Import angelegt wird, wenn noch keiner existiert,
    // indem wir die Methode via updatePricesAndStoreValuation NICHT nutzen.

    // Trigger: wir rufen importMetals und danach explizit getDashboard() (legt Valuation ohnehin an).
    service.importMetals(List.of(coin), java.time.LocalDate.of(2026, 3, 26));
    // simulate the side-effect after import
    // (call via public importEdelmetallFromCSV would require real resource; instead validate helper behavior)
    // We can call getDashboard which calls ensureInitialValuationExistsOnce; but we want ensureInitialPriceAndValuationAfterImport.
    // Therefore, we assert via repository interactions that ensureInitialPriceAndValuationAfterImport created both.

    // call the method under test via reflection (keeps production API unchanged)
    try {
      var m = EdelmetallService.class.getDeclaredMethod("ensureInitialPriceAndValuationAfterImport");
      m.setAccessible(true);
      m.invoke(service);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ArgumentCaptor<MetalPriceSnapshot> snapCap = ArgumentCaptor.forClass(MetalPriceSnapshot.class);
    verify(priceRepo).save(snapCap.capture());
    assertThat(snapCap.getValue().getGoldPriceEurPerOunce()).isEqualTo(3864.38);
    assertThat(snapCap.getValue().getSilverPriceEurPerOunce()).isEqualTo(58.83);

    verify(valuationRepo).save(any(MetalValuationSnapshot.class));
  }

  @Test
  void import_doesNotCreateInitialPriceSnapshot_whenOneAlreadyExists() {
    PreciousMetalRepository metalRepo = Mockito.mock(PreciousMetalRepository.class);
    MetalPriceSnapshotRepository priceRepo = Mockito.mock(MetalPriceSnapshotRepository.class);
    MetalValuationSnapshotRepository valuationRepo = Mockito.mock(MetalValuationSnapshotRepository.class);
    MetalPriceClient client = Mockito.mock(MetalPriceClient.class);

    when(priceRepo.findTopByOrderByTimestampDesc()).thenReturn(Optional.of(MetalPriceSnapshot.builder()
        .timestamp(Instant.parse("2026-03-25T00:00:00Z"))
        .goldPriceEurPerOunce(1)
        .silverPriceEurPerOunce(1)
        .build()));

    EdelmetallService service = new EdelmetallService(metalRepo, priceRepo, valuationRepo, client,
        Clock.fixed(Instant.parse("2026-03-26T00:00:00Z"), ZoneOffset.UTC));

    try {
      var m = EdelmetallService.class.getDeclaredMethod("ensureInitialPriceAndValuationAfterImport");
      m.setAccessible(true);
      m.invoke(service);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    verify(priceRepo, never()).save(any());
    verify(valuationRepo, never()).save(any());
  }
}

