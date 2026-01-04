package com.colombus.clan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusClanApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusClanApplication.class, args);
    }
}