package com.colombus.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Configuration
@ConfigurationProperties(prefix = "cloud.aws")
@Getter @Setter
public class AwsConfig {
    
    private String region;
    private String accessKey;
    private String secretKey;

    @Bean
    public Region region(){
        return Region.of(region);
    }

    @Bean
    public StaticCredentialsProvider staticCredentialsProvider() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            accessKey,
            secretKey
        );
        return StaticCredentialsProvider.create(credentials);
    }
}
