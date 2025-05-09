package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
@Getter
@Setter
@Document(collection = "shoe")
public  class Shoe {
    private String name;
    private String typ;
    private String usSize;
    private Date dateBought;
    private double valueBought;
    private double valueStockX;
    private double winStockX;
    private Date updatedAt;


}