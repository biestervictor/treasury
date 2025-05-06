package org.example.treasury.service;

import org.example.treasury.model.Display;
import org.example.treasury.repository.DisplayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class DisplayService {

    @Autowired
    private DisplayRepository displayRepository;

    public List<Display> getAllDisplays() {
        return displayRepository.findAll();
    }

    public Display getDisplayById(String id) {
        return displayRepository.findById(id).orElse(null);
    }

    public Display saveDisplay(Display display) {
        return displayRepository.save(display);
    }

    public void deleteDisplay(String id) {
        displayRepository.deleteById(id);
    }

    // Neue Methode: Displays nach setCode suchen
    public List<Display> findBySetCodeIgnoreCase(String setCode) {
        return displayRepository.findBySetCodeIgnoreCase(setCode);
    }

    // Neue Methode: Displays nach type suchen
    public List<Display> getDisplaysByType(String type) {
        return displayRepository.findByType(type);
    }

    // Neue Methode: Displays nach Wertbereich suchen
    public List<Display> getDisplaysByValueRange(double minValue, double maxValue) {
        return displayRepository.findByValueBoughtBetween(minValue, maxValue);
    }

    public void saveDisplay() {
        Display display = new Display();
        display.setSetCode("DMR");
        display.setType("COL");
        display.setName("Dominaria Remastered");
        display.setValueBought(139.99);
        display.setVendor("Games-Island");
        display.setDateBought(new Date()); // Aktuelles Datum

        displayRepository.save(display);
    }
}