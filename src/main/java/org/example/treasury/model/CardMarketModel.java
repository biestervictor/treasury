package org.example.treasury.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public abstract class CardMarketModel {
  @Id
  private String id;
  protected LocalDate updatedAt;
  protected double currentValue;
  protected String url="";
  protected double valueBought;
  protected LocalDate dateBought;
  protected String name;
  protected boolean isSold;
  protected double soldPrice;
  protected String location="";
  protected String language="EN";
  protected List<Angebot> angebotList = new ArrayList<>();
  public Double getRelevantPreis() {
    if (angebotList == null || angebotList.isEmpty()) {
      return 0.0;
    }
    List<Double> preise = angebotList.stream()
        .map(Angebot::getPreis)
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());
    if (preise.isEmpty()) {
      return 0.0;
    }
    if (preise.size() == 1) {
      return preise.get(0);
    }
    double lowest = preise.get(0);
    double second = preise.get(1);
    if (lowest < second * 0.85) {
      return second;
    } else {
      return lowest;
    }
  }
}