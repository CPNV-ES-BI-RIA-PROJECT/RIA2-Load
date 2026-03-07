package com.load.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
// TODO Change with real data in week 5
@JsonIgnoreProperties(ignoreUnknown = true)
public record TestPayload(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("business_date") LocalDate businessDate,
        @JsonProperty("tables") List<TablePayload> tables
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TablePayload(
            @JsonProperty("name") String name,
            @JsonProperty("mode") String mode,
            @JsonProperty("rows") List<JsonNode> rows
    ) {}
}