package com.colombus.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusAuthApplication.class, args);
    }
}