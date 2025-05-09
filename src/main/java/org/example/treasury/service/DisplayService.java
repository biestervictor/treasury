package org.example.treasury.service;

import org.example.treasury.model.Display;
import org.example.treasury.repository.DisplayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
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


    // Liste von Displas speichern
    public void saveAllDisplays(List<Display> displays) {
         displayRepository.saveAll(displays);
    }


    public Map<String, Map<String, Map<String, Object>>> getAggregatedValues() {
        List<Display> displays = getAllDisplays(); // Holt alle Displays aus der Datenbank
        return displays.stream()
                .collect(Collectors.groupingBy(
                        Display::getSetCode,
                        Collectors.groupingBy(
                                Display::getType,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> {
                                            double totalValue = list.stream().mapToDouble(Display::getValueBought).sum();
                                            long count = list.size();
                                            double averagePrice = count > 0 ? totalValue / count : 0;
                                            Map<String, Object> result = new HashMap<>();
                                            System.out.println("---------------------------");
                                            System.out.println("Total Value: " + totalValue);
                                            result.put("totalValue", totalValue);
                                            result.put("count", count);
                                            System.out.println("Count: " + count);
                                            System.out.println("Set Code: " + list.getFirst().getSetCode());
                                            System.out.println("Average Price: " + averagePrice);
                                            result.put("averagePrice", averagePrice);
                                            return  result ;
                                        }
                                )
                        )
                ));
    }

}