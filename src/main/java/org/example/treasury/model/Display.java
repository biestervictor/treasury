package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private Date dateBought;
    private String name;
    private Date updatedAt;
    private double currentValue;
}