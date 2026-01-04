package com.colombus.media.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(Region awsRegion, StaticCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(Region region, StaticCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}