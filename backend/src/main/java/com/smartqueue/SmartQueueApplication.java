package com.smartqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartQueueApplication.class, args);
    }
}
