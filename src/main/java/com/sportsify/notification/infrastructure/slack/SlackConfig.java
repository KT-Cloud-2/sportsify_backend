package com.sportsify.notification.infrastructure.slack;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SlackConfig {

    @Bean
    @Qualifier("slackRestClient")
    public RestClient slackRestClient() {
        return RestClient.builder().build();
    }
}
