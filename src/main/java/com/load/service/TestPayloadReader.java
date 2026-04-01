package com.load.service;

import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TestPayloadReader {
    private static final DateTimeFormatter PAYLOAD_LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter PAYLOAD_OFFSET_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");
    private static final DateTimeFormatter PAYLOAD_SQL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ROW_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JsonMapper jsonMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "JsonMapper is a Spring-managed shared dependency configured at startup"
    )
    public TestPayloadReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public TestPayload read(byte[] bytes) {
        JsonNode payloadNode = unwrapPayloadNode(jsonMapper.readTree(bytes));

        return new TestPayload(
                textValue(payloadNode, "uid"),
                textValue(payloadNode, "dtstamp"),
                firstNonBlank(
                        textValue(payloadNode, "dtstart"),
                        nestedTextValue(payloadNode, "start", "value")
                ),
                firstNonBlank(
                        textValue(payloadNode, "dtend"),
                        nestedTextValue(payloadNode, "end", "value")
                ),
                textValue(payloadNode, "summary"),
                textValue(payloadNode, "description"),
                firstNonBlank(
                        textValue(payloadNode, "categories"),
                        listValue(payloadNode, "categories")
                ),
                textValue(payloadNode, "organizer"),
                firstNonBlank(
                        textValue(payloadNode, "attendee"),
                        listValue(payloadNode, "attendees")
                ),
                textValue(payloadNode, "location"),
                firstNonBlank(
                        textValue(payloadNode, "timezone"),
                        nestedTextValue(payloadNode, "start", "timezone"),
                        nestedTextValue(payloadNode, "end", "timezone")
                )
        );
    }

    public EventRow asEvent(TestPayload payload) {
        return new EventRow(
                payload.uid(),
                normalizeDateTime(payload.dtstamp()),
                normalizeDateTime(payload.dtstart()),
                normalizeDateTime(payload.dtend()),
                payload.summary(),
                payload.description(),
                payload.categories(),
                payload.organizer(),
                payload.attendee(),
                payload.location(),
                payload.timezone()
        );
    }

    private String normalizeDateTime(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String normalizedValue = value.trim();

        try {
            if (normalizedValue.endsWith("Z")) {
                return OffsetDateTime.parse(normalizedValue, PAYLOAD_OFFSET_DATE_TIME)
                        .toLocalDateTime()
                        .format(ROW_DATE_TIME);
            }

            return parseLocalDateTime(normalizedValue).format(ROW_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + value, e);
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        List<DateTimeFormatter> formatters = List.of(
                PAYLOAD_LOCAL_DATE_TIME,
                PAYLOAD_SQL_DATE_TIME
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported local format.
            }
        }

        throw new DateTimeParseException("Unsupported local date-time format", value, 0);
    }

    private JsonNode unwrapPayloadNode(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Payload is empty");
        }
        if (!node.isArray()) {
            return node;
        }
        if (node.size() != 1) {
            throw new IllegalArgumentException("Payload array must contain exactly one event");
        }

        JsonNode firstNode = node.get(0);
        if (firstNode == null || firstNode.isNull()) {
            throw new IllegalArgumentException("Payload is empty");
        }
        return firstNode;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() && field.isValueNode() ? field.asText() : null;
    }

    private String nestedTextValue(JsonNode node, String objectFieldName, String fieldName) {
        JsonNode objectNode = node.get(objectFieldName);
        if (objectNode == null || objectNode.isNull() || !objectNode.isObject()) {
            return null;
        }

        return textValue(objectNode, fieldName);
    }

    private String listValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isArray()) {
            return null;
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : field) {
            if (item != null && !item.isNull() && item.isValueNode()) {
                values.add(item.asText());
            }
        }

        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return values.length == 0 ? null : values[0];
    }
}
