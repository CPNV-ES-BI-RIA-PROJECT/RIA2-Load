package com.load.service;

import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPayloadReaderTest {

    private final TestPayloadReader reader = new TestPayloadReader(new JsonMapper());

    @Test
    void shouldReadJsonPayloadAndIgnoreUnknownProperties() {
        /*
         * Feature: Test payload deserialization
         * Scenario: Reading a valid payload received from Alice's remote source
         * Given a JSON payload containing known fields and extra unexpected data
         * When the payload is read
         * Then the reader returns the expected business payload and ignores unknown properties
         */
        byte[] json = """
                {
                  "uid": "evt-123",
                  "dtstamp": "20260109T100000Z",
                  "dtstart": "20260109T110000",
                  "dtend": "20260109T120000Z",
                  "summary": "Load test",
                  "description": "Imported event",
                  "categories": "training",
                  "organizer": "organizer@example.com",
                  "attendee": "attendee@example.com",
                  "location": "Lausanne",
                  "timezone": "Europe/Zurich",
                  "unexpected": "ignored"
                }
                """.getBytes(StandardCharsets.UTF_8);

        TestPayload payload = reader.read(json);

        assertEquals(new TestPayload(
                "evt-123",
                "20260109T100000Z",
                "20260109T110000",
                "20260109T120000Z",
                "Load test",
                "Imported event",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        ), payload);
    }

    @Test
    void shouldRejectMalformedJsonPayload() {
        /*
         * Feature: Test payload deserialization
         * Scenario: Rejecting malformed remote JSON
         * Given a payload whose JSON structure is broken
         * When the payload is read
         * Then the reader propagates the JSON parsing failure
         */
        byte[] malformedJson = """
                {"uid":"evt-123"
                """.getBytes(StandardCharsets.UTF_8);

        assertThrows(JacksonException.class, () -> reader.read(malformedJson));
    }

    @Test
    void shouldConvertUtcAndLocalPayloadDatesIntoEventRowDates() {
        /*
         * Feature: Event row normalization
         * Scenario: Converting payload timestamps into SQL row timestamps
         * Given an event payload containing both UTC and local compact date formats
         * When the payload is transformed into an event row
         * Then each supported date format is normalized to the SQL timestamp format
         */
        TestPayload payload = new TestPayload(
                "evt-123",
                "20260109T100000Z",
                "20260109T110000",
                "20260109T120000Z",
                "Load test",
                "Imported event",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        );

        EventRow event = reader.asEvent(payload);

        assertEquals(new EventRow(
                "evt-123",
                "2026-01-09 10:00:00",
                "2026-01-09 11:00:00",
                "2026-01-09 12:00:00",
                "Load test",
                "Imported event",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        ), event);
    }

    @Test
    void shouldPreserveNullAndBlankDateValuesWhenTransformingPayload() {
        /*
         * Feature: Event row normalization
         * Scenario: Keeping absent or blank timestamps unchanged
         * Given an event payload with unknown or blank date values
         * When the payload is transformed into an event row
         * Then null and blank values remain unchanged instead of being reformatted
         */
        TestPayload payload = new TestPayload(
                "evt-blank",
                null,
                "",
                "   ",
                "No dates",
                "Edge case",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        );

        EventRow event = reader.asEvent(payload);

        assertEquals(new EventRow(
                "evt-blank",
                null,
                "",
                "   ",
                "No dates",
                "Edge case",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        ), event);
    }

    @Test
    void shouldRejectUnsupportedDateFormatsWhenTransformingPayload() {
        /*
         * Feature: Event row normalization
         * Scenario: Rejecting timestamps that do not follow the supported payload formats
         * Given an event payload with an invalid timestamp
         * When the payload is transformed into an event row
         * Then the reader raises a business-facing validation error with the parsing cause
         */
        TestPayload payload = new TestPayload(
                "evt-invalid",
                "2026-01-09 10:00:00",
                "20260109T110000",
                "20260109T120000",
                "Load test",
                "Invalid timestamp",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        );

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> reader.asEvent(payload));

        assertEquals("Invalid date format: 2026-01-09 10:00:00", failure.getMessage());
        assertInstanceOf(DateTimeParseException.class, failure.getCause());
    }

    @Test
    void shouldFailFastWhenPayloadIsMissing() {
        /*
         * Feature: Event row normalization
         * Scenario: Rejecting a missing payload
         * Given no payload was read from the remote source
         * When the payload is transformed into an event row
         * Then the reader fails immediately instead of creating an invalid event row
         */
        assertThrows(NullPointerException.class, () -> reader.asEvent(null));
    }
}
