package com.sportsify.notification.infrastructure.sender;

import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttNotificationSender implements NotificationSender {

    private final MqttClient mqttClient;

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.MQTT;
    }

    @Override
    public void send(String target, String subject, String body) {
        try {
            MqttMessage message = new MqttMessage(body.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            mqttClient.publish(target, message);
            log.info("MQTT published topic={}", target);
        } catch (MqttException e) {
            throw new RuntimeException("MQTT 발송 실패 target=" + target, e);
        }
    }
}
