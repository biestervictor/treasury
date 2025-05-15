package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * PreciousMetal is a class that represents a precious metal item.
 */

@Getter
@Setter
@Document(collection = "preciousMetal")

public class PreciousMetal {
  @Id
  private String id;
  private String size;
  private String type;
  private double valueBought;


}