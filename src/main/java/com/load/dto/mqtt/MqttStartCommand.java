package com.load.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MqttStartCommand(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("job_id") String jobId,
        @JsonProperty("input") Input input,
        @JsonProperty("options") Map<String, Object> options
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Input(@JsonProperty("uri") String uri) {}
}
