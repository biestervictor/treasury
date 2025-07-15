package org.example.treasury.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@AllArgsConstructor
@Document(collection = "secretLair")
public class SecretLair extends CardMarketModel {

  private boolean isDeck;
  boolean isFoil;


  /**
   * Default constructor initializes updatedAt and dateBought to the current date.
   */
  public SecretLair() {
    super();
    this.updatedAt = LocalDate.now();
    isSold=false;
    isDeck=false;

  }

}