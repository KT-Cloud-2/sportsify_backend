package com.sportsify.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id:notification-service}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.getBytes());
        }
        client.connect(options);
        log.info("MQTT connected brokerUrl={}", brokerUrl);
        return client;
    }
}
