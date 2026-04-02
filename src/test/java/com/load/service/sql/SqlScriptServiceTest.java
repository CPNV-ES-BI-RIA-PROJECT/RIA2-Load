package com.load.service.sql;

import com.load.dto.Rows.EventRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptServiceTest {

    private static final DateTimeFormatter REMOTE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final SqlScriptService service = new SqlScriptService();

    @Test
    void shouldGenerateInsertStatementForCompleteEvent() {
        /*
         * Feature: SQL script generation for imported events
         * Scenario: Generating an insert script for a complete event
         * Given an event for Alice with every field populated
         * When the SQL script is generated
         * Then the service returns a complete INSERT statement with escaped text values
         */
        EventRow event = new EventRow(
                "evt-123",
                "2026-01-09 10:00:00",
                "2026-01-09 11:00:00",
                "2026-01-09 12:00:00",
                "Alice's load test",
                "Imported from Bob's calendar",
                "training",
                "organizer@example.com",
                "attendee@example.com",
                "Lausanne",
                "Europe/Zurich"
        );

        String expected = "INSERT INTO events (\n"
                + "    uid,\n"
                + "    dtstamp,\n"
                + "    dtstart,\n"
                + "    dtend,\n"
                + "    summary,\n"
                + "    description,\n"
                + "    categories,\n"
                + "    organizer,\n"
                + "    attendee,\n"
                + "    location,\n"
                + "    timezone\n"
                + ")\n"
                + "VALUES (\n"
                + "           'evt-123',\n"
                + "           '2026-01-09 10:00:00',\n"
                + "           '2026-01-09 11:00:00',\n"
                + "           '2026-01-09 12:00:00',\n"
                + "           'Alice''s load test',\n"
                + "           'Imported from Bob''s calendar',\n"
                + "           'training',\n"
                + "           'organizer@example.com',\n"
                + "           'attendee@example.com',\n"
                + "           'Lausanne',\n"
                + "           'Europe/Zurich'\n"
                + "       );";

        assertEquals(expected, service.generate(event));
    }

    @Test
    void shouldGenerateInsertStatementWithNullAndEmptyValues() {
        /*
         * Feature: SQL script generation for incomplete events
         * Scenario: Preserving null and empty values in the generated SQL
         * Given an event where some values are unknown and others are intentionally empty
         * When the SQL script is generated
         * Then null values become NULL and empty text remains an empty SQL string
         */
        EventRow event = new EventRow(
                null,
                "",
                null,
                "",
                null,
                "",
                null,
                "",
                null,
                "",
                null
        );

        String expected = "INSERT INTO events (\n"
                + "    uid,\n"
                + "    dtstamp,\n"
                + "    dtstart,\n"
                + "    dtend,\n"
                + "    summary,\n"
                + "    description,\n"
                + "    categories,\n"
                + "    organizer,\n"
                + "    attendee,\n"
                + "    location,\n"
                + "    timezone\n"
                + ")\n"
                + "VALUES (\n"
                + "           NULL,\n"
                + "           '',\n"
                + "           NULL,\n"
                + "           '',\n"
                + "           NULL,\n"
                + "           '',\n"
                + "           NULL,\n"
                + "           '',\n"
                + "           NULL,\n"
                + "           '',\n"
                + "           NULL\n"
                + "       );";

        assertEquals(expected, service.generate(event));
    }

    @Test
    void shouldFailFastWhenEventIsMissing() {
        /*
         * Feature: SQL script generation input validation
         * Scenario: Rejecting a missing event
         * Given no event data is available
         * When the SQL script is generated
         * Then the service fails immediately instead of creating invalid SQL
         */
        assertThrows(NullPointerException.class, () -> service.generate(null));
    }

    @Test
    void shouldGenerateTimestampedRemotePathWithSqlExtension() {
        /*
         * Feature: SQL script remote path generation
         * Scenario: Creating the storage path for a generated SQL script
         * Given a new SQL script needs to be uploaded
         * When the remote path is generated
         * Then it uses the expected prefix, timestamp format and .sql extension
         */
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        String remotePath = service.generateRemotePath();

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        assertTrue(remotePath.startsWith("bi1-julien/load/"));
        assertTrue(remotePath.endsWith(".sql"));

        String timestamp = remotePath.substring("bi1-julien/load/".length(), remotePath.length() - 4);
        LocalDateTime parsedTimestamp = LocalDateTime.parse(timestamp, REMOTE_TIMESTAMP_FORMAT);

        assertFalse(parsedTimestamp.isBefore(before));
        assertFalse(parsedTimestamp.isAfter(after));
    }

    @Test
    void shouldConvertJsonRemoteToSqlRemote() {
        assertEquals("jobs/123.sql", service.deriveSqlRemote("jobs/123.json"));
    }

    @Test
    void shouldAppendSqlExtensionWhenRemoteHasNoExtension() {
        assertEquals("jobs/123.sql", service.deriveSqlRemote("jobs/123"));
    }
}
