package org.example.treasury;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.example.treasury.controller", "org.example.treasury.service", "org.example.treasury.model", "org.example.treasury.repository"})
public class TreasuryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreasuryApplication.class, args);
    }

}