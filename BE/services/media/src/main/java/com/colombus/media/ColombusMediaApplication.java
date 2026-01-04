package com.colombus.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusMediaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusMediaApplication.class, args);
    }
}
