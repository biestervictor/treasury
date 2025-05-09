package org.example.treasury.service;

import org.example.treasury.model.Shoe;
import org.example.treasury.repository.ShoeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShoeService {


    private final ShoeRepository shoeRepository;

    public ShoeService(ShoeRepository shoeRepository) {
        this.shoeRepository = shoeRepository;
    }
    // Alle Schuhe abrufen
    public List<Shoe> getAllShoes() {
        return shoeRepository.findAll();
    }

    // Liste von Schuhen speichern
    public void saveAllShoes(List<Shoe> shoes) {
      shoeRepository.saveAll(shoes);
    }

}