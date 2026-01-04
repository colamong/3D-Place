package com.colombus.leaderboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.colombus")
public class ColombusLeaderboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(ColombusLeaderboardApplication.class, args);
    }
}
