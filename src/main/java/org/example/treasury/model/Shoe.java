package org.example.treasury.model;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Shoe is a class that represents a shoe item.
 * It contains information about the name, type, US size, date bought, value bought,
 * value on StockX, win on StockX, and the last updated date.
 */

@Getter
@Setter
@Document(collection = "shoe")

public class Shoe {
  private String name;
  private String typ;
  private String usSize;
  private Date dateBought;
  private double valueBought;
  private double valueStockX;
  private double winStockX;
  private Date updatedAt;


}