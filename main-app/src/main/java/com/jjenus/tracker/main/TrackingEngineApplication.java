package com.jjenus.tracker.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.jjenus.tracker")
@EnableScheduling
public class TrackingEngineApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TrackingEngineApplication.class, args);
    }
}