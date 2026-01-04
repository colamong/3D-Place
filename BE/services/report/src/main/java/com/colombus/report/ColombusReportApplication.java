package com.colombus.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusReportApplication.class, args);
    }
}