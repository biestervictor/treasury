package org.example.treasury.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

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

    // Getter und Setter

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String get_id() {
        return _id;
    }

    public String getVendor() {
        return vendor;
    }

    public Date getDateBought() {
        return dateBought;
    }

    public void setDateBought(Date dateBought) {
        this.dateBought = dateBought;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getSetCode() {
        return setCode;
    }

    public void setSetCode(String setCode) {
        this.setCode = setCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getValueBought() {
        return valueBought;
    }

    public void setValueBought(double valueBought) {
        this.valueBought = valueBought;
    }
}