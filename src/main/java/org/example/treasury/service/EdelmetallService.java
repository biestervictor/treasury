package org.example.treasury.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.model.MetalPriceSnapshot;
import org.example.treasury.model.MetalValuationSnapshot;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;
import org.example.treasury.repository.MetalPriceSnapshotRepository;
import org.example.treasury.repository.MetalValuationSnapshotRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EdelmetallService {

  private static final Logger log = LoggerFactory.getLogger(EdelmetallService.class);

  // Fallback-Preise (EUR pro Unze) falls noch kein Snapshot vorhanden ist.
  // Gewünscht: Silber 58,83 €/oz und Gold 3864,38 €/oz.
  private static final double DEFAULT_GOLD_PRICE_PER_OUNCE = 3864.38;
  private static final double DEFAULT_SILVER_PRICE_PER_OUNCE = 58.83;

  // Umrechnung: Edelmetallpreise werden üblicherweise pro Troy-Unze angegeben.
  private static final double GRAMS_PER_TROY_OUNCE = 31.1034768;

  static Double parseEuroNumber(String input) {
    if (input == null) {
      return null;
    }
    String s = input.trim();
    if (s.isBlank()) {
      return null;
    }
    // Unterstützt sowohl "3864,38" als auch "3864.38".
    // Zusätzlich: tausenderpunkte entfernen ("3.864,38" => "3864,38").
    s = s.replace(" ", "");
    if (s.contains(",")) {
      // deutsches Format: '.' ist sehr wahrscheinlich Tausendertrenner
      s = s.replace(".", "");
      s = s.replace(',', '.');
    }
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private final PreciousMetalRepository preciousMetalRepository;
  private final MetalPriceSnapshotRepository metalPriceSnapshotRepository;
  private final MetalValuationSnapshotRepository metalValuationSnapshotRepository;
  private final MetalPriceClient metalPriceClient;
  private final Clock clock;

  @Autowired
  public EdelmetallService(PreciousMetalRepository preciousMetalRepository,
                           MetalPriceSnapshotRepository metalPriceSnapshotRepository,
                           MetalValuationSnapshotRepository metalValuationSnapshotRepository,
                           MetalPriceClient metalPriceClient) {
    this(preciousMetalRepository, metalPriceSnapshotRepository, metalValuationSnapshotRepository, metalPriceClient,
        Clock.systemUTC());
  }

  EdelmetallService(PreciousMetalRepository preciousMetalRepository,
                    MetalPriceSnapshotRepository metalPriceSnapshotRepository,
                    MetalValuationSnapshotRepository metalValuationSnapshotRepository,
                    MetalPriceClient metalPriceClient,
                    Clock clock) {
    this.preciousMetalRepository = preciousMetalRepository;
    this.metalPriceSnapshotRepository = metalPriceSnapshotRepository;
    this.metalValuationSnapshotRepository = metalValuationSnapshotRepository;
    this.metalPriceClient = metalPriceClient;
    this.clock = clock;
  }

  /**
   * Importiert {@code src/main/resources/Edelmetalle.csv} in MongoDB.
   *
   * @return Anzahl importierter Zeilen
   */
  public int importEdelmetallFromCSV() {
    var parser = new EdelmetallCsvParser();

    try {
      ClassPathResource resource = new ClassPathResource("Edelmetalle.csv");
      List<PreciousMetal> metals = parser.parse(resource.getInputStream());

      LocalDate now = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
      int imported = importMetals(metals, now);

      // Gewünscht: Nach dem Import einmalig einen initialen Price-Snapshot anhand der Default-Preise anlegen
      // und direkt eine Bewertung (pro Münze inkl. Gewinn) für heute speichern.
      ensureInitialPriceAndValuationAfterImport();

      return imported;
    } catch (IOException e) {
      throw new IllegalStateException("Konnte Edelmetalle.csv nicht importieren", e);
    }
  }

  private void ensureInitialPriceAndValuationAfterImport() {
    if (metalPriceSnapshotRepository.findTopByOrderByTimestampDesc().isPresent()) {
      return;
    }

    Instant now = Instant.now(clock);
    MetalPriceSnapshot defaultPriceSnap = MetalPriceSnapshot.builder()
        .timestamp(now)
        .goldPriceEurPerOunce(DEFAULT_GOLD_PRICE_PER_OUNCE)
        .silverPriceEurPerOunce(DEFAULT_SILVER_PRICE_PER_OUNCE)
        .build();

    MetalPriceSnapshot stored = metalPriceSnapshotRepository.save(defaultPriceSnap);
    // Pro Münze bewerten (Gewinn/aktueller Wert) und als Snapshot speichern.
    storeValuationSnapshot(stored);
  }

  /**
   * Import-Logik extrahiert für Tests/Idempotenz.
   *
   * @return Anzahl neu importierter Metals
   */
  int importMetals(List<PreciousMetal> metals, LocalDate importedAt) {
    if (metals == null || metals.isEmpty()) {
      return 0;
    }

    int imported = 0;
    for (PreciousMetal m : metals) {
      if (m == null) {
        continue;
      }
      m.setImportedAt(importedAt);
      m.setImportKey(buildImportKey(m));

      if (m.getImportKey() == null || m.getImportKey().isBlank()) {
        continue;
      }
      if (preciousMetalRepository.existsByImportKey(m.getImportKey())) {
        continue;
      }

      preciousMetalRepository.save(m);
      imported++;
    }

    return imported;
  }

  static String buildImportKey(PreciousMetal m) {
    if (m == null) {
      return null;
    }
    // Normalisieren, damit Imports reproduzierbar sind (Trim, whitespace collapse, lower-case)
    String name = m.getName() == null ? "" : m.getName().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    String type = m.getType() == null ? "" : m.getType().name();
    String year = m.getYear() == null ? "" : String.valueOf(m.getYear());

    // Floats: nicht 1:1 als String, daher runden wir auf 4 Nachkommastellen (für Gramm/Preis ausreichend).
    String weight = String.format(Locale.ROOT, "%.4f", m.getWeightInGrams());
    String price = String.format(Locale.ROOT, "%.4f", m.getPurchasePrice());

    return String.join("|",
        name,
        year,
        String.valueOf(m.getQuantity()),
        weight,
        type,
        price);
  }

  /**
   * Ruft aktuelle Preise ab und speichert einen Snapshot.
   */
  public MetalPriceSnapshot updatePrices() {
    MetalPriceClient.Prices prices = metalPriceClient.fetchCurrentPrices();
    MetalPriceSnapshot snap = MetalPriceSnapshot.builder()
        .timestamp(prices.timestamp() == null ? Instant.now(clock) : prices.timestamp())
        .goldPriceEurPerOunce(prices.goldEurPerOunce())
        .silverPriceEurPerOunce(prices.silverEurPerOunce())
        .build();

    return metalPriceSnapshotRepository.save(snap);
  }

  /**
   * Klick-Action: Holt aktuelle Preise, speichert Preis-Snapshot und speichert zusätzlich eine Bewertung
   * (aktueller Wert + Gewinn) pro Münze/Position mit heutigem Datum.
   */
  public MetalValuationSnapshot updatePricesAndStoreValuation() {
    try {
      MetalPriceSnapshot snap = updatePrices();
      return storeValuationSnapshot(snap);
    } catch (Exception e) {
      log.warn("Preisabruf fehlgeschlagen, es wird kein Wert gespeichert: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Speichert manuell eingegebene Preise (EUR/oz) + Bewertung pro Münze (Gewinn) ohne externen API-Call.
   */
  public MetalValuationSnapshot storeManualPricesAndValuation(ManualMetalPricesRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request darf nicht null sein");
    }
    Double gold = parseEuroNumber(request.goldEurPerOunce());
    Double silver = parseEuroNumber(request.silverEurPerOunce());
    if (gold == null || silver == null) {
      throw new IllegalArgumentException("Ungültige Preise. Erwartet z.B. '3864,38' oder '3864.38'.");
    }

    Instant now = Instant.now(clock);
    MetalPriceSnapshot snap = MetalPriceSnapshot.builder()
        .timestamp(now)
        .goldPriceEurPerOunce(gold)
        .silverPriceEurPerOunce(silver)
        .build();

    MetalPriceSnapshot stored = metalPriceSnapshotRepository.save(snap);
    return storeValuationSnapshot(stored);
  }

  /**
   * Läuft regelmäßig und hält die Preise aktuell.
   * Default: alle 6 Stunden.
   */
  @Scheduled(cron = "0 0 */6 * * *")
  public void scheduledUpdatePrices() {
    try {
      updatePrices();
    } catch (Exception e) {
      log.warn("scheduledUpdatePrices fehlgeschlagen: {}", e.getMessage());
    }
  }

  public MetalDashboardDto getDashboard() {
    ensureInitialValuationExistsOnce();

    Instant now = Instant.now(clock);
    MetalPriceSnapshot currentPriceSnapshot = metalPriceSnapshotRepository.findTopByOrderByTimestampDesc().orElse(null);

    MetalDashboardDto.PriceDto currentPrices = currentPriceSnapshot == null
        ? new MetalDashboardDto.PriceDto(DEFAULT_GOLD_PRICE_PER_OUNCE, DEFAULT_SILVER_PRICE_PER_OUNCE, now)
        : new MetalDashboardDto.PriceDto(currentPriceSnapshot.getGoldPriceEurPerOunce(),
            currentPriceSnapshot.getSilverPriceEurPerOunce(), currentPriceSnapshot.getTimestamp());

    List<MetalValuationSnapshot> valuations = metalValuationSnapshotRepository.findAllByOrderByTimestampAsc();
    var timeline = valuations.stream()
        .map(v -> new MetalDashboardDto.ProfitPointDto(v.getTimestamp(), v.getTotalProfit()))
        .collect(Collectors.toList());

    var marketValueTimeline = valuations.stream()
        .map(v -> new MetalDashboardDto.MarketValuePointDto(v.getTimestamp(), v.getTotalCurrentValue()))
        .collect(Collectors.toList());

    List<MetalValuationSnapshot.ItemValuation> latestItems = valuations.isEmpty() || valuations.getLast().getItems() == null
        ? List.of()
        : valuations.getLast().getItems();

    double currentProfitTotal = valuations.isEmpty() ? 0.0 : valuations.getLast().getTotalProfit();
    double currentMarketValueTotal = valuations.isEmpty() ? 0.0 : valuations.getLast().getTotalCurrentValue();

    return new MetalDashboardDto(currentPrices, timeline, latestItems, marketValueTimeline, currentProfitTotal,
        currentMarketValueTotal);
  }

  private void ensureInitialValuationExistsOnce() {
    if (metalValuationSnapshotRepository.findTopByOrderByTimestampDesc().isPresent()) {
      return;
    }

    // Einmalig: initiale Bewertung (Defaultpreise) anlegen, damit Diagramm auch ohne Preisermittlung startet.
    Instant now = Instant.now(clock);
    MetalPriceSnapshot defaultSnap = MetalPriceSnapshot.builder()
        .timestamp(now)
        .goldPriceEurPerOunce(DEFAULT_GOLD_PRICE_PER_OUNCE)
        .silverPriceEurPerOunce(DEFAULT_SILVER_PRICE_PER_OUNCE)
        .build();

    metalValuationSnapshotRepository.save(buildValuationSnapshot(now, defaultSnap));
  }

  private MetalValuationSnapshot storeValuationSnapshot(MetalPriceSnapshot priceSnapshot) {
    Instant timestamp = priceSnapshot == null || priceSnapshot.getTimestamp() == null
        ? Instant.now(clock)
        : priceSnapshot.getTimestamp();
    MetalValuationSnapshot valuation = buildValuationSnapshot(timestamp, priceSnapshot);
    return metalValuationSnapshotRepository.save(valuation);
  }

  private MetalValuationSnapshot buildValuationSnapshot(Instant timestamp, MetalPriceSnapshot priceSnapshot) {
    List<PreciousMetal> metals = preciousMetalRepository.findAllByImportedAtIsNotNull();

    List<MetalValuationSnapshot.ItemValuation> items = metals.stream()
        .filter(Objects::nonNull)
        .map(m -> {
          double pricePerOunce = m.getType() == PreciousMetalType.GOLD
              ? priceSnapshot.getGoldPriceEurPerOunce()
              : priceSnapshot.getSilverPriceEurPerOunce();
          double ounces = m.getWeightInGrams() / GRAMS_PER_TROY_OUNCE;
          double currentUnitValue = ounces * pricePerOunce;
          double currentTotalValue = currentUnitValue * m.getQuantity();
          double profit = m.getQuantity() * (currentUnitValue - m.getPurchasePrice());

          return MetalValuationSnapshot.ItemValuation.builder()
              .preciousMetalId(m.getId())
              .name(m.getName())
              .type(m.getType())
              .weightInGrams(m.getWeightInGrams())
              .quantity(m.getQuantity())
              .priceEurPerOunce(pricePerOunce)
              .currentUnitValue(currentUnitValue)
              .currentTotalValue(currentTotalValue)
              .purchasePrice(m.getPurchasePrice())
              .profit(profit)
              .build();
        })
        .toList();

    double totalCurrentValue = items.stream().mapToDouble(MetalValuationSnapshot.ItemValuation::getCurrentTotalValue).sum();
    double totalProfit = items.stream().mapToDouble(MetalValuationSnapshot.ItemValuation::getProfit).sum();

    return MetalValuationSnapshot.builder()
        .timestamp(timestamp)
        .items(items)
        .totalCurrentValue(totalCurrentValue)
        .totalProfit(totalProfit)
        .build();
  }

}

