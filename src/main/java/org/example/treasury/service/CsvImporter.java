package org.example.treasury.service;

import org.example.treasury.model.Display;
import org.example.treasury.model.DisplayType;
import org.example.treasury.model.Shoe;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvImporter {

    public List<Shoe> importCsv(String filePath) {
        List<Shoe> schuhe = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine(); // Kopfzeile überspringen
            if (headerLine == null) {
                throw new IOException("Die CSV-Datei ist leer.");
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(";");
                if (values.length < 7) continue; // Überspringe unvollständige Zeilen

                try {
                    Shoe shoe = new Shoe();
                    shoe.setName(values[1]);
                    shoe.setTyp(values[2]);
                    shoe.setUsSize(values[3]);
                    shoe.setDateBought(dateFormat.parse(values[4]));
                    shoe.setValueBought(Double.parseDouble(values[5].replace("€", "").replace(",", ".")));
                    shoe.setValueStockX(Double.parseDouble(values[6].replace("€", "").replace(",", ".")));
                    shoe.setWinStockX(shoe.getValueStockX() - shoe.getValueBought());

                    schuhe.add(shoe);
                } catch (ParseException | NumberFormatException e) {
                    e.printStackTrace(); // Fehlerhafte Zeile überspringen
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return schuhe;
    }
    public List<Display> importDisplayCsv(String filePath) {
        List<Display> displays = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine(); // Kopfzeile überspringen
            if (headerLine == null) {
                throw new IOException("Die CSV-Datei ist leer.");
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(";");


                try {
                    Display display = new Display();
                   // display.setName();
display.setSetCode(values[0].split("-")[0]);
display.setType(convertDisplayType(values[0].split("-")[1]));
display.setValueBought(Double.parseDouble(values[1].replace(",",".")));
if(values.length>2){
display.setVendor(values[2]);}else {display.setVendor("Nicht dokumentiert");}

                    displays.add(display);
                } catch ( NumberFormatException e) {
                    e.printStackTrace(); // Fehlerhafte Zeile überspringen
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return displays;
    }
    private String convertDisplayType(String type){

        if(type.equals("D")){
            return DisplayType.DRAFT.name();
        }else  if(type.equals("P")){
            return DisplayType.PRERELEASE.name();
        }else  if(type.equals("C")){
            return DisplayType.COLLECTOR.name();
        }else  if(type.equals("S")){
            return DisplayType.SET.name();
        }else  if(type.equals("F")){
            return DisplayType.BUNDLE.name();
        }
        return "error";
    }
}