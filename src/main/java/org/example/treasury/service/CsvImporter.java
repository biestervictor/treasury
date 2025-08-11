package org.example.treasury.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.example.treasury.model.Display;
import org.example.treasury.model.DisplayType;
import org.example.treasury.model.MagicSet;
import org.example.treasury.model.SecretLair;
import org.example.treasury.model.Shoe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Die Klasse CsvImporter importiert Daten aus CSV-Dateien und konvertiert sie in Java-Objekte.
 */

@Service
public class CsvImporter {

  private final List<MagicSet> magicSets;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Erstellt eine neue Instanz von CsvImporter mit dem angegebenen ScryFall-Webservice.
   *
   * @param scryFallWebservice der Webservice für die ScryFall-API
   */

  public CsvImporter(ScryFallWebservice scryFallWebservice) {

    List<MagicSet> sets;
    try {
      sets = scryFallWebservice.getSetList();
    } catch (Exception e) {
      logger.error("Import fehlgeschlagen", e);
      sets = new ArrayList<>();
    }
    this.magicSets = sets;
  }

  /**
   * Importiert eine CSV-Datei mit Schuhdaten und gibt eine Liste von Shoe-Objekten zurück.
   *
   * @param filePath Pfad zur CSV-Datei
   * @return Liste von Shoe-Objekten
   */

  public List<Shoe> importCsv(String filePath) {
    List<Shoe> shoes = new ArrayList<>();
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");

    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
      String headerLine = br.readLine(); // Kopfzeile überspringen
      if (headerLine == null) {
        logger.error("Die CSV-Datei ist leer.");
        throw new IOException("Die CSV-Datei ist leer.");
      }

      String line;
      while ((line = br.readLine()) != null) {
        String[] values = line.split(";");
        if (values.length < 7) {
          continue; // Überspringe unvollständige Zeilen
        }

        try {
          Shoe shoe = new Shoe();
          shoe.setName(values[1]);
          shoe.setTyp(values[2]);
          shoe.setUsSize(values[3]);
          shoe.setDateBought(dateFormat.parse(values[4]));
          shoe.setValueBought(Double.parseDouble(values[5].replace("€", "").replace(",", ".")));
          shoe.setValueStockX(Double.parseDouble(values[6].replace("€", "").replace(",", ".")));
          shoe.setWinStockX(shoe.getValueStockX() - shoe.getValueBought());

          shoes.add(shoe);
        } catch (ParseException | NumberFormatException e) {
          logger.error("Die Zeile konnte nicht geparsed werden.", e);
        }
      }
    } catch (IOException e) {
      logger.error("Fehler beim importieren", e);
    }

    return shoes;
  }

  /**
   * Importiert eine CSV-Datei mit Display-Daten und gibt eine Liste von Display-Objekten zurück.
   *
   * @param filePath Pfad zur CSV-Datei
   * @return Liste von Display-Objekten
   */

  public List<Display> importDisplayCsv(String filePath) {
    List<Display> displays = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
      String headerLine = br.readLine(); // Kopfzeile überspringen
      if (headerLine == null) {
        logger.error("Die CSV-Datei ist leer.");
        throw new IOException("Die CSV-Datei ist leer.");
      }

      String line;
      while ((line = br.readLine()) != null) {
        String[] values = line.split(";");


        try {
          Display display = new Display();
          // display.setName();
          display.setSetCode(values[0].split("-")[0].toLowerCase());
          display.setType(convertDisplayType(values[0].split("-")[1]));
          logger.info(display.getSetCode().toLowerCase());

          //TODO mistery booster existier tnicht... mapping auf MB2.. erledigt da cmb2 korrket ist?
          if (!display.getSetCode().equalsIgnoreCase("mys")) {
            MagicSet magicSet = magicSets.stream()
                .filter(set -> set.getCode().equalsIgnoreCase(display.getSetCode()
                    .toLowerCase())).findFirst().orElse(null);

            if (magicSet != null) {
              display.setName(magicSet.getName());
              LocalDate releaseOfDraftBoosters = magicSets.stream().filter( s -> s.getCode().equals("mkm")).findFirst().get().getReleaseDate();
             //AB MKM gab es playbooster
              if(display.getType().equals("DRAFT") && !magicSet.getReleaseDate().isBefore(releaseOfDraftBoosters)) {
                display.setType(DisplayType.PLAY.name());
              }
            } else {
              display.setName("Unbekanntes Set");
            }

          }
          display.setValueBought(Double.parseDouble(values[1].replace(",", ".")));
          if (values.length > 2) {
            display.setVendor(values[2]);
          } else {
            display.setVendor("Nicht dokumentiert");
          }

          displays.add(display);
        } catch (NumberFormatException e) {
          logger.error("Die Zeile konnte nicht geparsed werden.", e);
        }
      }
    } catch (IOException e) {
      logger.error("Fehler beim importieren", e);
    }

    return displays;
  }

  private String convertDisplayType(String type) {
    return switch (type) {
      case "D" -> DisplayType.DRAFT.name();
      case "P" -> DisplayType.PRERELEASE.name();
      case "C" -> DisplayType.COLLECTOR.name();
      case "S" -> DisplayType.SET.name();
      case "F" -> DisplayType.BUNDLE.name();
      default -> "error";
    };
  }
  public List<SecretLair> importSecretLairCsv(String filePath) {
    List<SecretLair> secretLairs = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
      String headerLine = br.readLine(); // Kopfzeile überspringen
      if (headerLine == null) {
        logger.error("Die CSV-Datei ist leer.");
        throw new IOException("Die CSV-Datei ist leer.");
      }
//Name,Menge,Normal,Foil,Einzelpreis
      String line;
      while ((line = br.readLine()) != null) {
        String[] values = line.split(",");
        try {
          SecretLair sl = new SecretLair();
          sl.setName(values[0].trim());
          if(sl.getName().isEmpty() && sl.getName().toLowerCase().contains("deck")) {
           sl.setDeck(true);
          }
          int quantity = Integer.parseInt(values[1]);
          sl.setValueBought(Double.parseDouble(values[4].replace(",", ".")));
          if (values[3]!=null && values[3].toLowerCase().contains("x")) {
            sl.setFoil(true);
          }
          for(int n = 0; n < quantity; n++) {
            secretLairs.add(sl);
          }

        } catch (NumberFormatException e) {
          logger.error("Die Zeile konnte nicht geparsed werden.", e);
        }
      }
    } catch (IOException e) {
      logger.error("Fehler beim importieren", e);
    }

    return secretLairs;
  }
}