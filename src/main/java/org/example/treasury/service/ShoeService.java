package org.example.treasury.service;

import org.example.treasury.model.Shoe;
import org.example.treasury.repository.ShoeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShoeService {

    @Autowired
    private ShoeRepository shoeRepository;

    // Alle Schuhe abrufen
    public List<Shoe> getAllShoes() {
        return shoeRepository.findAll();
    }

    // Schuh nach ID abrufen
    public Shoe getShoeById(String id) {
        return shoeRepository.findById(id).orElse(null);
    }

    // Einzelnen Schuh speichern oder aktualisieren
    public Shoe saveOrUpdateShoe(Shoe shoe) {
        return shoeRepository.save(shoe);
    }

    // Liste von Schuhen speichern
    public List<Shoe> saveAllShoes(List<Shoe> shoes) {
        return shoeRepository.saveAll(shoes);
    }

    // Schuh nach ID löschen
    public void deleteShoe(String id) {
        shoeRepository.deleteById(id);
    }
}