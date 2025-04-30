package org.example.treasury.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document(collection = "preciousMetal")
public class PreciousMetal {






        @Id
        private String _id;
        private String size;
        private String type;
        private double valueBought;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }


        // Getter und Setter
        public String get_id() {
            return _id;
        }

        public void set_id(String _id) {
            this._id = _id;
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
