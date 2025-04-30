package org.example.treasury.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "displays")
public class Display {

    @Id
    private String _id;
    private String setCode;
    private String type;
    private double valueBought;

    // Getter und Setter
    public String get_id() {
        return _id;
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