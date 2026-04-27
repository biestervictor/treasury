package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.example.treasury.model.MagicSet;
import org.example.treasury.repository.MagicSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MagicSetService#updateBoosterBoxImages(Map)}.
 */
class MagicSetServiceBoosterImageTest {

  private MagicSetRepository repository;
  private MagicSetService service;

  @BeforeEach
  void setUp() {
    repository = mock(MagicSetRepository.class);
    service = new MagicSetService(repository);
  }

  @Test
  void updateBoosterBoxImages_matchingSetCode_setsImageUrl() {
    MagicSet iko = MagicSet.builder().code("IKO").name("Ikoria").build();
    when(repository.findAll()).thenReturn(List.of(iko));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    service.updateBoosterBoxImages(
        Map.of("IKO", "https://static.mtgstocks.com/sealedimage/t239.png"));

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t239.png",
        iko.getBoosterBoxImageUrl());
  }

  @Test
  void updateBoosterBoxImages_noMatchingSetCode_leavesUrlNull() {
    MagicSet war = MagicSet.builder().code("WAR").name("War of the Spark").build();
    when(repository.findAll()).thenReturn(List.of(war));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    service.updateBoosterBoxImages(Map.of("IKO", "https://example.com/t239.png"));

    assertNull(war.getBoosterBoxImageUrl());
  }

  @Test
  void updateBoosterBoxImages_savesAllSets() {
    MagicSet s1 = MagicSet.builder().code("IKO").build();
    MagicSet s2 = MagicSet.builder().code("ZNR").build();
    when(repository.findAll()).thenReturn(List.of(s1, s2));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    service.updateBoosterBoxImages(
        Map.of("IKO", "https://static.mtgstocks.com/sealedimage/t239.png"));

    verify(repository).saveAll(List.of(s1, s2));
  }

  @Test
  void updateBoosterBoxImages_setCodeCaseInsensitive_updatesSet() {
    MagicSet iko = MagicSet.builder().code("iko").name("Ikoria").build();
    when(repository.findAll()).thenReturn(List.of(iko));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    service.updateBoosterBoxImages(
        Map.of("IKO", "https://static.mtgstocks.com/sealedimage/t239.png"));

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t239.png",
        iko.getBoosterBoxImageUrl());
  }
}
