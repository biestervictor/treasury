package org.example.treasury.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Display is a class that represents a display item.
 */

@Getter
@Setter
@Document(collection = "displays")

public class Display  extends CardMarketModel {


  private String setCode;
  private String type;
  private String vendor;




  /**
   * Default constructor initializes updatedAt and dateBought to the current date.
   */
  public Display() {
    super();
    this.updatedAt = LocalDate.now();
    this.dateBought = LocalDate.now();
    isSold=false;
  }
}