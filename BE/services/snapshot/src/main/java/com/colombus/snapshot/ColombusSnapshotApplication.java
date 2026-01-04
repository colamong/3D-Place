package com.colombus.snapshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusSnapshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusSnapshotApplication.class, args);
    }
}