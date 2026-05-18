package com.sportsify.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableResilientMethods
public class AsyncConfig {
}
