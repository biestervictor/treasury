package org.example.treasury.model;


import lombok.*;

import java.time.LocalDate;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class MagicSet {

    private String code;
    private String name;
    private String uri;
    private String iconUri;
    private LocalDate releaseDate;
    private int cardCount;


}