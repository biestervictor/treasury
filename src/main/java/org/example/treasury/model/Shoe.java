package org.example.treasury.model;

import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTyp() {
        return typ;
    }

    public void setTyp(String typ) {
        this.typ = typ;
    }

    public String getUsSize() {
        return usSize;
    }

    public void setUsSize(String usSize) {
        this.usSize = usSize;
    }

    public Date getDateBought() {
        return dateBought;
    }

    public void setDateBought(Date dateBought) {
        this.dateBought = dateBought;
    }

    public double getValueBought() {
        return valueBought;
    }

    public void setValueBought(double valueBought) {
        this.valueBought = valueBought;
    }

    public double getValueStockX() {
        return valueStockX;
    }

    public void setValueStockX(double valueStockX) {
        this.valueStockX = valueStockX;
    }

    public double getWinStockX() {
        return winStockX;
    }

    public void setWinStockX(double winStockX) {
        this.winStockX = winStockX;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}