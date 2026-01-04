package com.colombus.world;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusWorldApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusWorldApplication.class, args);
    }
}