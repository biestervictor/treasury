package org.example.treasury.model;

import java.time.LocalDate;
import lombok.Builder;
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

public class Display {

  @Id
  private String id;
  private String setCode;
  private String type;
  private double valueBought;
  private String vendor;
  private LocalDate dateBought;
  private String name;
  private LocalDate updatedAt;
  private double currentValue;

  /**
   * Default constructor initializes updatedAt and dateBought to the current date.
   */
  public Display() {
    this.updatedAt=LocalDate.now();
    this.dateBought=LocalDate.now();
  }
}