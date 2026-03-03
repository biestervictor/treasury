package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.example.treasury.repository.DisplayRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DisplayServiceUpdateAngeboteTest {

  private static final String ID = "1";
  private static final double EXISTING_CURRENT_VALUE = 123.45;
  private static final LocalDate EXISTING_UPDATED_AT = LocalDate.of(2020, 1, 1);

  private static final String SET_CODE = "KHM";
  private static final String TYPE = "DRAFT";
  private static final String URL = "https://example.test/new-url";

  @Test
  void updateAngeboteBySetCodeAndType_doesNotUpdateUpdatedAtOrPrice_whenNewOffersEmpty() {
    DisplayRepository repo = mockRepo();
    DisplayService service = new DisplayService(repo, null);

    Display existing = existingDisplay();
    stubFindBySetCode(repo, existing);

    AtomicReference<Iterable<Display>> savedRef = captureSaveAllArgument(repo, existing);

    Display incoming = incomingDisplay(Collections.emptyList());
    incoming.setCurrentValue(999.99); // darf NICHT durchschlagen

    service.updateAngeboteBySetCodeAndType(incoming);

    Display savedDisplay = firstSaved(savedRef);

    // URL wird immer aktualisiert
    assertEquals(URL, savedDisplay.getUrl());

    // Aber updatedAt und currentValue bleiben unverändert, wenn neue Angebote leer sind
    assertEquals(EXISTING_UPDATED_AT, savedDisplay.getUpdatedAt());
    assertEquals(EXISTING_CURRENT_VALUE, savedDisplay.getCurrentValue(), 0.0001);
  }

  @Test
  void updateAngeboteBySetCodeAndType_updatesUpdatedAtAndPrice_whenNewOffersPresent() {
    DisplayRepository repo = mockRepo();
    DisplayService service = new DisplayService(repo, null);

    Display existing = existingDisplay();
    stubFindBySetCode(repo, existing);

    AtomicReference<Iterable<Display>> savedRef = captureSaveAllArgument(repo, existing);

    List<Angebot> neueAngebote = List.of(
        new Angebot("offer-1", 10.0, "1"),
        new Angebot("offer-2", 12.0, "1")
    );

    Display incoming = incomingDisplay(neueAngebote);

    service.updateAngeboteBySetCodeAndType(incoming);

    Display savedDisplay = firstSaved(savedRef);

    // URL wird immer aktualisiert
    assertEquals(URL, savedDisplay.getUrl());

    // updatedAt wird gesetzt, wenn neue Angebote vorhanden sind
    assertEquals(LocalDate.now(), savedDisplay.getUpdatedAt());

    // getRelevantPreis() filtert starke Ausreißer: 10.0 < 12.0 * 0.85 -> relevant ist dann 12.0
    assertEquals(12.0, savedDisplay.getCurrentValue(), 0.0001);

    // Angebotliste wird übernommen
    assertEquals(2, savedDisplay.getAngebotList().size());
    assertEquals(10.0, savedDisplay.getAngebotList().getFirst().getPreis(), 0.0001);
  }

  @Test
  void updateAngeboteBySetCodeAndType_setsLowestPrice_whenNoOutlierPresent() {
    DisplayRepository repo = mockRepo();
    DisplayService service = new DisplayService(repo, null);

    Display existing = existingDisplay();
    stubFindBySetCode(repo, existing);

    AtomicReference<Iterable<Display>> savedRef = captureSaveAllArgument(repo, existing);

    // Kein Ausreißer: 10.0 ist NICHT < 10.5 * 0.85 (8.925) -> relevant bleibt lowest (10.0)
    List<Angebot> neueAngebote = List.of(
        new Angebot("offer-1", 10.0, "1"),
        new Angebot("offer-2", 10.5, "1")
    );

    Display incoming = incomingDisplay(neueAngebote);

    service.updateAngeboteBySetCodeAndType(incoming);

    Display savedDisplay = firstSaved(savedRef);

    assertEquals(URL, savedDisplay.getUrl());
    assertEquals(LocalDate.now(), savedDisplay.getUpdatedAt());
    assertEquals(10.0, savedDisplay.getCurrentValue(), 0.0001);
  }

  private static DisplayRepository mockRepo() {
    return Mockito.mock(DisplayRepository.class);
  }

  private static Display existingDisplay() {
    Display existing = new Display();
    existing.setId(ID);
    existing.setSetCode(SET_CODE);
    existing.setType(TYPE);
    existing.setCurrentValue(EXISTING_CURRENT_VALUE);
    existing.setUpdatedAt(EXISTING_UPDATED_AT);
    return existing;
  }

  private static Display incomingDisplay(List<Angebot> neueAngebote) {
    Display incoming = new Display();
    incoming.setSetCode(SET_CODE);
    incoming.setType(TYPE);
    incoming.setUrl(URL);
    incoming.setAngebotList(neueAngebote);
    return incoming;
  }

  private static void stubFindBySetCode(DisplayRepository repo, Display existing) {
    when(repo.findBySetCodeIgnoreCase(SET_CODE.toLowerCase())).thenReturn(List.of(existing));
  }

  private static AtomicReference<Iterable<Display>> captureSaveAllArgument(
      DisplayRepository repo, Display existing) {
    AtomicReference<Iterable<Display>> savedRef = new AtomicReference<>();
    doAnswer(invocation -> {
      Iterable<Display> arg = invocation.getArgument(0);
      savedRef.set(arg);
      return List.of(existing);
    }).when(repo).saveAll(Mockito.any());
    return savedRef;
  }

  private static Display firstSaved(AtomicReference<Iterable<Display>> savedRef) {
    Iterable<Display> savedIterable = savedRef.get();
    assertNotNull(savedIterable);
    return savedIterable.iterator().next();
  }
}
