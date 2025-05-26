package org.example.treasury;

import org.example.treasury.repository.DisplayRepository;
import org.example.treasury.repository.MagicSetRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.example.treasury.repository.ShoeRepository;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

  @Bean
  public ShoeRepository shoeRepository() {
    return Mockito.mock(ShoeRepository.class);
  }
  @Bean
  public MagicSetRepository magicSetRepository() {
    return Mockito.mock(MagicSetRepository.class);
  }


  @Bean
  public DisplayRepository displayRepository() {
    return Mockito.mock(DisplayRepository.class);
  }

  @Bean
  public PreciousMetalRepository preciousMetalRepository() {
    return Mockito.mock(PreciousMetalRepository.class);
  }

}