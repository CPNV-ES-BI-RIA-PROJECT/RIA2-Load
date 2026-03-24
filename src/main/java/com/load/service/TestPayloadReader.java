package com.load.service;

import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class TestPayloadReader {
    private static final DateTimeFormatter PAYLOAD_LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter PAYLOAD_OFFSET_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");
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
        return jsonMapper.readValue(bytes, TestPayload.class);
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

        try {
            if (value.endsWith("Z")) {
                return OffsetDateTime.parse(value, PAYLOAD_OFFSET_DATE_TIME)
                        .toLocalDateTime()
                        .format(ROW_DATE_TIME);
            }

            return LocalDateTime.parse(value, PAYLOAD_LOCAL_DATE_TIME)
                    .format(ROW_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + value, e);
        }
    }
}
