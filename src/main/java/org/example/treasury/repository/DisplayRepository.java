package org.example.treasury.repository;

import org.example.treasury.model.Display;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisplayRepository extends MongoRepository<Display, String> {
    // Benutzerdefinierte Abfrage, um Displays nach setCode zu finden
    List<Display> findBySetCode(String setCode);

    // Benutzerdefinierte Abfrage, um Displays nach type zu finden
    List<Display> findByType(String type);

    // Benutzerdefinierte Abfrage, um Displays mit einem bestimmten Wertbereich zu finden
    List<Display> findByValueBoughtBetween(double minValue, double maxValue);

    @Query("{ 'setCode': { $regex: ?0, $options: 'i' } }")
    List<Display> findBySetCodeIgnoreCase(String setCode);
}