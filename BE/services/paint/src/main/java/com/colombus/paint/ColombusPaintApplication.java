package com.colombus.paint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.colombus")
@EnableScheduling
public class ColombusPaintApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusPaintApplication.class, args);
    }
}