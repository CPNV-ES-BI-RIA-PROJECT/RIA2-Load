package com.load.service.mqtt;

import com.load.config.mqtt.EtlMqttProperties;
import com.load.dto.LoadImportResult;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "etl.mqtt", name = "enabled", havingValue = "true")
public class MqttEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttEventPublisher.class);

    private final EtlMqttProperties properties;
    private final MessageChannel mqttOutputChannel;
    private final JsonMapper jsonMapper;

    public MqttEventPublisher(
            EtlMqttProperties properties,
            MessageChannel mqttOutputChannel,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.mqttOutputChannel = mqttOutputChannel;
        this.jsonMapper = jsonMapper;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public void publishRunning(String jobId, int progress) {
        Map<String, Object> payload = basePayload(jobId);
        payload.put("progress", progress);
        send(properties.eventRunningTopic(), payload);
    }

    public void publishCompleted(String jobId, LoadImportResult result) {
        Map<String, Object> payload = basePayload(jobId);
        String outputUri = hasText(result.shareUrl()) ? result.shareUrl() : result.bucketRemote();
        payload.put(
                "output",
                Map.of(
                        "uri", outputUri,
                        "remote", result.bucketRemote()
                )
        );
        send(properties.eventCompletedTopic(), payload);
    }

    public void publishFailed(String jobId, String code, String message) {
        Map<String, Object> payload = basePayload(jobId);
        payload.put(
                "error",
                Map.of(
                        "code", code,
                        "message", message
                )
        );
        send(properties.eventFailedTopic(), payload);
    }

    private Map<String, Object> basePayload(String jobId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", properties.getSchemaVersion());
        payload.put("job_id", jobId);
        return payload;
    }

    private void send(String topic, Object payload) {
        Message<String> message = MessageBuilder
                .withPayload(toJson(payload))
                .setHeader(MqttHeaders.TOPIC, topic)
                .setHeader(MqttHeaders.QOS, 1)
                .build();

        boolean sent = mqttOutputChannel.send(message);
        if (!sent) {
            log.warn("MQTT event not sent on topic={}", topic);
        }
    }

    private String toJson(Object payload) {
        return jsonMapper.writeValueAsString(payload);
    }
}
