package com.load.service.mqtt;

import com.load.config.mqtt.EtlMqttProperties;
import com.load.dto.LoadImportResult;
import com.load.dto.mqtt.MqttStartCommand;
import com.load.service.importer.LoadImportService;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "etl.mqtt", name = "enabled", havingValue = "true")
public class MqttCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttCommandHandler.class);

    private final JsonMapper jsonMapper;
    private final EtlMqttProperties properties;
    private final LoadImportService loadImportService;
    private final MqttEventPublisher eventPublisher;

    public MqttCommandHandler(
            JsonMapper jsonMapper,
            EtlMqttProperties properties,
            LoadImportService loadImportService,
            MqttEventPublisher eventPublisher
    ) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.loadImportService = loadImportService;
        this.eventPublisher = eventPublisher;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleStartCommand(Message<?> message) {
        MqttStartCommand command;
        try {
            command = parseCommand(message.getPayload());
        } catch (Exception e) {
            log.warn("Ignoring MQTT message: invalid JSON payload", e);
            return;
        }

        String jobId = trimToNull(command.jobId());
        String validationError = validateCommand(command);
        if (validationError != null) {
            log.warn("Ignoring MQTT start command: {}", validationError);
            if (jobId != null) {
                eventPublisher.publishFailed(jobId, "LOAD_INVALID_COMMAND", validationError);
            }
            return;
        }

        String remote = resolveRemote(jobId, command.options());
        eventPublisher.publishRunning(jobId, 0);

        try {
            LoadImportResult result = loadImportService.importFromUrl(remote, command.input().uri());
            eventPublisher.publishCompleted(jobId, result);
        } catch (Exception e) {
            log.error("MQTT job failed. job_id={}", jobId, e);
            eventPublisher.publishFailed(jobId, errorCodeFor(e), failureMessage(e));
        }
    }

    private MqttStartCommand parseCommand(Object payload) throws IOException {
        if (payload instanceof byte[] bytes) {
            return jsonMapper.readValue(bytes, MqttStartCommand.class);
        }
        if (payload instanceof String text) {
            return jsonMapper.readValue(text, MqttStartCommand.class);
        }
        throw new IllegalArgumentException("Unsupported MQTT payload type: " + payload.getClass().getName());
    }

    private String validateCommand(MqttStartCommand command) {
        if (command == null) {
            return "payload is required";
        }
        if (!properties.getSchemaVersion().equals(command.schemaVersion())) {
            return "schemaVersion must be " + properties.getSchemaVersion();
        }
        if (trimToNull(command.jobId()) == null) {
            return "job_id is required";
        }
        if (command.input() == null || trimToNull(command.input().uri()) == null) {
            return "input.uri is required";
        }
        return null;
    }

    private String resolveRemote(String jobId, Map<String, Object> options) {
        if (options != null) {
            Object remote = options.get("remote");
            if (remote instanceof String remoteText && !remoteText.isBlank()) {
                return remoteText;
            }
        }
        return "jobs/" + jobId + ".json";
    }

    private static String errorCodeFor(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return "LOAD_VALIDATION_ERROR";
        }
        if (exception instanceof IllegalStateException) {
            return "LOAD_EXECUTION_ERROR";
        }
        return "LOAD_INTERNAL_ERROR";
    }

    private static String failureMessage(Exception exception) {
        String message = trimToNull(exception.getMessage());
        if (message != null) {
            return message;
        }
        return "Unexpected load error";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
