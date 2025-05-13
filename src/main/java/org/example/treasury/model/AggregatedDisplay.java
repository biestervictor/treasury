package org.example.treasury.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregatedDisplay {
    private String setCode;
    private String type;
    private long count;
    private double averagePrice;
    private String iconUri;


}