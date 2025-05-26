package org.example.treasury.model;


import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

/**
 * MagicSet is a class that represents a Magic: The Gathering set.
 * It contains information about the set code, name, URI, icon URI, release date, and card count.
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder

public class MagicSet {

  @Id
  private String code;
  private String name;
  private String uri;
  private String iconUri;
  private LocalDate releaseDate;
  private int cardCount;


}