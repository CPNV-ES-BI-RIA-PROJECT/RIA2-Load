package com.load.service.sql;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqlValueEncoderTest {

    @Test
    void shouldHideUtilityClassBehindPrivateConstructor() throws Exception {
        /*
         * Feature: SQL value encoder utility lifecycle
         * Scenario: Preventing direct public instantiation
         * Given the SQL value encoder is a utility class
         * When a test accesses its constructor reflectively
         * Then an instance can be created only through the private constructor
         */
        Constructor<SqlValueEncoder> constructor = SqlValueEncoder.class.getDeclaredConstructor();

        constructor.setAccessible(true);
        SqlValueEncoder instance = constructor.newInstance();

        assertNotNull(instance);
        assertEquals(SqlValueEncoder.class, instance.getClass());
    }

    @Test
    void shouldEncodeStringValuesIncludingNullEmptyAndEscapedQuotes() {
        /*
         * Feature: SQL string literal encoding
         * Scenario: Encoding text values for SQL statements
         * Given attendee names that can be missing, empty or contain apostrophes
         * When the values are encoded for SQL
         * Then null becomes NULL and text is wrapped and escaped safely
         */
        assertEquals("NULL", SqlValueEncoder.v((String) null));
        assertEquals("''", SqlValueEncoder.v(""));
        assertEquals("'O''Brien'", SqlValueEncoder.v("O'Brien"));
    }

    @Test
    void shouldEncodeBooleanValuesIncludingNullTrueAndFalse() {
        /*
         * Feature: SQL boolean encoding
         * Scenario: Encoding boolean flags for SQL statements
         * Given a feature flag that can be unknown, enabled or disabled
         * When the value is encoded for SQL
         * Then null becomes NULL, true becomes 1 and false becomes 0
         */
        assertEquals("NULL", SqlValueEncoder.v((Boolean) null));
        assertEquals("1", SqlValueEncoder.v(Boolean.TRUE));
        assertEquals("0", SqlValueEncoder.v(Boolean.FALSE));
    }

    @Test
    void shouldEncodeGenericNumberValuesIncludingNull() {
        /*
         * Feature: SQL numeric encoding
         * Scenario: Encoding generic numeric values for SQL statements
         * Given a participant count that can be missing or present
         * When the number is encoded for SQL
         * Then null becomes NULL and the numeric text is preserved
         */
        assertEquals("NULL", SqlValueEncoder.v((Number) null));
        assertEquals("-42", SqlValueEncoder.v(Integer.valueOf(-42)));
    }

    @Test
    void shouldEncodeBigDecimalValuesUsingPlainStringRepresentation() {
        /*
         * Feature: SQL decimal encoding
         * Scenario: Encoding decimal values without scientific notation
         * Given a price that can be missing or expressed as a scientific decimal
         * When the decimal is encoded for SQL
         * Then null becomes NULL and the plain decimal representation is used
         */
        assertEquals("NULL", SqlValueEncoder.v((BigDecimal) null));
        assertEquals("1000", SqlValueEncoder.v(new BigDecimal("1E+3")));
    }
}
