package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.treasury.model.MagicSet;
import org.example.treasury.repository.MagicSetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link MagicSetService#saveAllMagicSets(List)}.
 * Verifies that {@code boosterBoxImageUrl} and {@code wishPrices} are preserved
 * from existing documents when a fresh Scryfall import would overwrite them with null.
 */
class MagicSetServiceSaveAllTest {

  @Test
  void saveAllMagicSets_preservesBoosterBoxImageUrl_whenNewSetHasNone() {
    MagicSetRepository repo = Mockito.mock(MagicSetRepository.class);

    MagicSet existing = MagicSet.builder()
        .code("ZNR")
        .name("Zendikar Rising")
        .releaseDate(LocalDate.of(2020, 9, 25))
        .boosterBoxImageUrl("https://example.com/znr.jpg")
        .build();
    when(repo.findAll()).thenReturn(List.of(existing));
    when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    MagicSetService service = new MagicSetService(repo);

    MagicSet freshFromScryfall = MagicSet.builder()
        .code("ZNR")
        .name("Zendikar Rising")
        .releaseDate(LocalDate.of(2020, 9, 25))
        .boosterBoxImageUrl(null)
        .build();

    List<MagicSet> saved = service.saveAllMagicSets(List.of(freshFromScryfall));

    assertEquals("https://example.com/znr.jpg", saved.get(0).getBoosterBoxImageUrl());
  }

  @Test
  void saveAllMagicSets_preservesWishPrices_whenNewSetHasNone() {
    MagicSetRepository repo = Mockito.mock(MagicSetRepository.class);

    Map<String, Double> wishPrices = new HashMap<>();
    wishPrices.put("DRAFT", 89.99);
    wishPrices.put("COLLECTOR", 145.00);

    MagicSet existing = MagicSet.builder()
        .code("MH3")
        .name("Modern Horizons 3")
        .releaseDate(LocalDate.of(2024, 6, 14))
        .wishPrices(wishPrices)
        .build();
    when(repo.findAll()).thenReturn(List.of(existing));
    when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    MagicSetService service = new MagicSetService(repo);

    MagicSet freshFromScryfall = MagicSet.builder()
        .code("MH3")
        .name("Modern Horizons 3")
        .releaseDate(LocalDate.of(2024, 6, 14))
        .wishPrices(null)
        .build();

    List<MagicSet> saved = service.saveAllMagicSets(List.of(freshFromScryfall));

    Map<String, Double> result = saved.get(0).getWishPrices();
    assertNotNull(result);
    assertEquals(89.99, result.get("DRAFT"));
    assertEquals(145.00, result.get("COLLECTOR"));
  }

  @Test
  void saveAllMagicSets_doesNotPreserveBoosterBoxImageUrl_whenExistingHasNone() {
    MagicSetRepository repo = Mockito.mock(MagicSetRepository.class);

    MagicSet existing = MagicSet.builder()
        .code("BLB")
        .name("Bloomburrow")
        .releaseDate(LocalDate.of(2024, 8, 2))
        .boosterBoxImageUrl(null)
        .build();
    when(repo.findAll()).thenReturn(List.of(existing));
    when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    MagicSetService service = new MagicSetService(repo);

    MagicSet freshFromScryfall = MagicSet.builder()
        .code("BLB")
        .name("Bloomburrow")
        .releaseDate(LocalDate.of(2024, 8, 2))
        .boosterBoxImageUrl(null)
        .build();

    List<MagicSet> saved = service.saveAllMagicSets(List.of(freshFromScryfall));

    assertNull(saved.get(0).getBoosterBoxImageUrl());
  }

  @Test
  void saveAllMagicSets_newSetNotInExisting_isPassedThrough() {
    MagicSetRepository repo = Mockito.mock(MagicSetRepository.class);

    when(repo.findAll()).thenReturn(List.of());
    when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    MagicSetService service = new MagicSetService(repo);

    MagicSet freshSet = MagicSet.builder()
        .code("DSK")
        .name("Duskmourn")
        .releaseDate(LocalDate.of(2024, 9, 27))
        .build();

    List<MagicSet> saved = service.saveAllMagicSets(List.of(freshSet));

    assertEquals("DSK", saved.get(0).getCode());
    assertNull(saved.get(0).getBoosterBoxImageUrl());
  }
}
