package com.colombus.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusWsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusWsApplication.class, args);
    }
}