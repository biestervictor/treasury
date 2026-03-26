package org.example.treasury.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;

/**
 * Parser für die Datei {@code Edelmetalle.csv} (Semikolon-separiert).
 *
 * <p>Die Datei enthält Vorspann/Leerzeilen. Wir parsen nur Zeilen, die mindestens
 * Bezeichnung, Jahr, Anzahl, Gewicht, Einkaufspreis enthalten.</p>
 */
public class EdelmetallCsvParser {

  public List<PreciousMetal> parse(InputStream in) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      List<PreciousMetal> result = new ArrayList<>();
      String line;
      while ((line = br.readLine()) != null) {
        // skip empty/metadata lines
        if (line.isBlank() || line.startsWith("Tabelle") || line.startsWith("Einkaufspreise")) {
          continue;
        }

        String[] values = line.split(";", -1);
        // Data lines in our CSV begin with a name and have at least 7 columns.
        if (values.length < 7) {
          continue;
        }

        String name = values[0].trim();
        if (name.isEmpty()) {
          continue;
        }

        // Header row starts with "Bezeichnung"
        if (name.equalsIgnoreCase("Bezeichnung")) {
          continue;
        }

        // columns (based on sample):
        // 0 Bezeichnung
        // 1 Erscheinungsjahr
        // 2 Anzahl
        // 3 Gewicht in Gramm
        // 4 Einkaufspreis Typ:Gold
        // 5 Einkaufspreis Typ:Silber
        // 6 Gesamt Einkaufswert (ignored)

        Integer year = tryParseInt(values[1]);
        Integer quantity = tryParseInt(values[2]);
        Double weight = tryParseDoubleGerman(values[3]);
        Double goldPrice = tryParseDoubleGerman(values[4]);
        Double silverPrice = tryParseDoubleGerman(values[5]);

        if (quantity == null || weight == null) {
          continue;
        }

        PreciousMetalType type;
        double purchasePrice;
        if (goldPrice != null) {
          type = PreciousMetalType.GOLD;
          purchasePrice = goldPrice;
        } else if (silverPrice != null) {
          type = PreciousMetalType.SILVER;
          purchasePrice = silverPrice;
        } else {
          // no purchase price -> skip
          continue;
        }

        result.add(PreciousMetal.builder()
            .name(name)
            .year(year)
            .quantity(quantity)
            .weightInGrams(weight)
            .type(type)
            .purchasePrice(purchasePrice)
            .build());
      }
      return result;
    }
  }

  private static Integer tryParseInt(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    if (t.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(t);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Double tryParseDoubleGerman(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    if (t.isEmpty()) {
      return null;
    }
    try {
      return Double.parseDouble(t.replace("€", "").replace(" ", "").replace(",", "."));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

