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
    void shouldReadSingleEntryArrayPayloadWithNestedDatesAndLists() {
        /*
         * Feature: Test payload deserialization
         * Scenario: Reading the alternate calendar-style payload format
         * Given a JSON array containing one event with nested start/end objects and list fields
         * When the payload is read
         * Then the reader flattens it into the internal payload structure
         */
        byte[] json = """
                [
                  {
                    "uid": "john-20250303@mycompany.com",
                    "dtstamp": "2025-02-01 08:00:00",
                    "start": {
                      "value": "2025-03-03 09:00:00",
                      "timezone": "Europe/Bern"
                    },
                    "end": {
                      "value": "2025-03-03 15:00:00",
                      "timezone": "Europe/Bern"
                    },
                    "summary": "Work session",
                    "description": "[acme.ch] Development session",
                    "categories": ["BUSINESS"],
                    "organizer": "contact@acme.ch",
                    "attendees": ["john.doe@mycompany.com"],
                    "location": "https://maps.google.com/?q=46.2044,6.1432"
                  }
                ]
                """.getBytes(StandardCharsets.UTF_8);

        TestPayload payload = reader.read(json);

        assertEquals(new TestPayload(
                "john-20250303@mycompany.com",
                "2025-02-01 08:00:00",
                "2025-03-03 09:00:00",
                "2025-03-03 15:00:00",
                "Work session",
                "[acme.ch] Development session",
                "BUSINESS",
                "contact@acme.ch",
                "john.doe@mycompany.com",
                "https://maps.google.com/?q=46.2044,6.1432",
                "Europe/Bern"
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
    void shouldConvertSqlStylePayloadDatesIntoEventRowDates() {
        /*
         * Feature: Event row normalization
         * Scenario: Accepting SQL-style payload timestamps
         * Given an event payload containing date-times already formatted as yyyy-MM-dd HH:mm:ss
         * When the payload is transformed into an event row
         * Then the timestamps are accepted and preserved in SQL format
         */
        TestPayload payload = new TestPayload(
                "evt-sql-style",
                "2025-02-01 08:00:00",
                "2025-03-03 09:00:00",
                "2025-03-03 15:00:00",
                "Work session",
                "Imported event",
                "BUSINESS",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Bern"
        );

        EventRow event = reader.asEvent(payload);

        assertEquals(new EventRow(
                "evt-sql-style",
                "2025-02-01 08:00:00",
                "2025-03-03 09:00:00",
                "2025-03-03 15:00:00",
                "Work session",
                "Imported event",
                "BUSINESS",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Bern"
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
                "2026/01/09 10:00:00",
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

        assertEquals("Invalid date format: 2026/01/09 10:00:00", failure.getMessage());
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
