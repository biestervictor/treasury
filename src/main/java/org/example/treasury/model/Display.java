package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@Document(collection = "displays")
public class Display {

    @Id
    private String _id;
    private String setCode;
    private String type;
    private double valueBought;
    private String vendor;
    private LocalDate dateBought;
    private String name;
    private LocalDate updatedAt;
    private double currentValue;
    private LocalDate setReleaseDate;
    private String iconUri;
}