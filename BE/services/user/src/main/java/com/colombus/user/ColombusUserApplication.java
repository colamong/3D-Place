package com.colombus.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusUserApplication.class, args);
    }
}
