package com.colombus.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusBffApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ColombusBffApplication.class, args);
    }
}