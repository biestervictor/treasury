package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;
import org.junit.jupiter.api.Test;

class EdelmetallServiceImportTest {

  @Test
  void parse_shouldImportGoldAndSilverLines() throws Exception {
    String csv = "Tabelle 1\n"
        + "Bezeichnung;Erscheinungsjahr;Anzahl;Gewicht in Gramm;Einkaufspreis Typ:Gold;Einkaufspreis Typ:Silber;Gesamt Einkaufswert\n"
        + "Gold Coin;2020;2;31,1034768;100;;200\n"
        + "Silver Coin;2019;3;31,1034768;;20,26;60,78\n";

    EdelmetallCsvParser parser = new EdelmetallCsvParser();

    List<PreciousMetal> metals = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(metals).hasSize(2);

    PreciousMetal gold = metals.get(0);
    assertThat(gold.getName()).isEqualTo("Gold Coin");
    assertThat(gold.getYear()).isEqualTo(2020);
    assertThat(gold.getQuantity()).isEqualTo(2);
    assertThat(gold.getWeightInGrams()).isEqualTo(31.1034768);
    assertThat(gold.getType()).isEqualTo(PreciousMetalType.GOLD);
    assertThat(gold.getPurchasePrice()).isEqualTo(100.0);

    PreciousMetal silver = metals.get(1);
    assertThat(silver.getName()).isEqualTo("Silver Coin");
    assertThat(silver.getType()).isEqualTo(PreciousMetalType.SILVER);
    assertThat(silver.getPurchasePrice()).isEqualTo(20.26);
  }
}

