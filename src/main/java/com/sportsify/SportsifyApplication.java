package com.sportsify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class SportsifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SportsifyApplication.class, args);
    }
}
