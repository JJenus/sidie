package com.jjenus.tracker.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.jjenus.tracker.**",
        exclude = {}
)
@EnableScheduling
@EntityScan("com.jjenus.tracker.**")
@EnableJpaRepositories("com.jjenus.tracker.**")
@EnableRedisRepositories("com.jjenus.tracker.**")
public class TrackingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrackingEngineApplication.class, args);
    }
}