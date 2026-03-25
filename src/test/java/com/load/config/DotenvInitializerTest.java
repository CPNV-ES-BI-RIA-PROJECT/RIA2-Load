package com.load.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DotenvInitializerTest {

    private static final Path DOTENV_PATH = Path.of(".env");
    private static final String PUBLIC_KEY = "CODEX_TEST_PUBLIC_VALUE";
    private static final String SECRET_KEY = "CODEX_TEST_SECRET_KEY";
    private static final String PASSWORD_KEY = "CODEX_TEST_PASSWORD";
    private static final String CREDENTIALS_KEY = "CODEX_TEST_CREDENTIALS";
    private static final String API_KEY = "CODEX_TEST_API_KEY";

    @Test
    void shouldLoadDotenvEntriesIntoSystemProperties() throws Exception {
        /*
         * Feature: Dotenv bootstrap initialization
         * Scenario: Loading environment variables from a .env file before Spring starts
         * Given a .env file contains public and sensitive configuration values
         * When the initializer runs before bean creation
         * Then every entry is copied into system properties for Spring consumption
         */
        byte[] originalDotenv = readDotenvIfPresent();
        clearManagedProperties();

        try {
            Files.writeString(
                    DOTENV_PATH,
                    PUBLIC_KEY + "=visible-value\n"
                            + SECRET_KEY + "=ABCD1234WXYZ\n"
                            + PASSWORD_KEY + "=hunter22\n"
                            + CREDENTIALS_KEY + "=cred-value\n"
                            + API_KEY + "=key-value\n",
                    StandardCharsets.UTF_8
            );

            DotenvInitializer initializer = new DotenvInitializer();
            initializer.initialize(new GenericApplicationContext());

            assertEquals("visible-value", System.getProperty(PUBLIC_KEY));
            assertEquals("ABCD1234WXYZ", System.getProperty(SECRET_KEY));
            assertEquals("hunter22", System.getProperty(PASSWORD_KEY));
            assertEquals("cred-value", System.getProperty(CREDENTIALS_KEY));
            assertEquals("key-value", System.getProperty(API_KEY));
        } finally {
            restoreDotenv(originalDotenv);
            clearManagedProperties();
        }
    }

    @Test
    void shouldIgnoreMissingDotenvFile() throws Exception {
        /*
         * Feature: Dotenv bootstrap initialization
         * Scenario: Continuing startup when the .env file is absent
         * Given no .env file is available in the working directory
         * When the initializer runs before bean creation
         * Then startup continues without failing and no managed property is added
         */
        byte[] originalDotenv = readDotenvIfPresent();
        clearManagedProperties();

        try {
            Files.deleteIfExists(DOTENV_PATH);

            DotenvInitializer initializer = new DotenvInitializer();
            initializer.initialize(new GenericApplicationContext());

            assertNull(System.getProperty(PUBLIC_KEY));
        } finally {
            restoreDotenv(originalDotenv);
            clearManagedProperties();
        }
    }

    @Test
    void shouldMaskLongSensitiveValuesUsingVisibleEdges() throws Exception {
        /*
         * Feature: Dotenv logging hygiene
         * Scenario: Partially masking long sensitive values in debug logs
         * Given a sensitive value longer than eight characters
         * When the initializer formats it for logging
         * Then only the first and last four characters remain visible
         */
        DotenvInitializer initializer = new DotenvInitializer();

        assertEquals("ABCD...WXYZ", maskedValue(initializer, "ABCD1234WXYZ", SECRET_KEY));
        assertEquals("pass...1234", maskedValue(initializer, "passcode1234", PASSWORD_KEY));
        assertEquals("cred...alue", maskedValue(initializer, "credentialvalue", CREDENTIALS_KEY));
        assertEquals("key-...alue", maskedValue(initializer, "key-secret-value", API_KEY));
    }

    @Test
    void shouldMaskShortSensitiveValuesCompletely() throws Exception {
        /*
         * Feature: Dotenv logging hygiene
         * Scenario: Fully masking short sensitive values in debug logs
         * Given a sensitive value no longer than eight characters
         * When the initializer formats it for logging
         * Then the log output hides the entire value
         */
        DotenvInitializer initializer = new DotenvInitializer();

        assertEquals("***", maskedValue(initializer, "secret", SECRET_KEY));
    }

    @Test
    void shouldLeaveNonSensitiveValuesUnchangedInLogs() throws Exception {
        /*
         * Feature: Dotenv logging hygiene
         * Scenario: Leaving non-sensitive values visible in debug logs
         * Given a non-sensitive configuration value
         * When the initializer formats it for logging
         * Then the value remains unchanged
         */
        DotenvInitializer initializer = new DotenvInitializer();

        assertEquals("visible-value", maskedValue(initializer, "visible-value", "CODEX_TEST_REGION"));
    }

    private String maskedValue(DotenvInitializer initializer, String value, String key) throws Exception {
        Method method = DotenvInitializer.class.getDeclaredMethod("maskedValue", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(initializer, value, key);
    }

    private byte[] readDotenvIfPresent() throws Exception {
        return Files.exists(DOTENV_PATH) ? Files.readAllBytes(DOTENV_PATH) : null;
    }

    private void restoreDotenv(byte[] originalDotenv) throws Exception {
        if (originalDotenv == null) {
            Files.deleteIfExists(DOTENV_PATH);
            return;
        }

        Files.write(DOTENV_PATH, originalDotenv);
    }

    private void clearManagedProperties() {
        System.clearProperty(PUBLIC_KEY);
        System.clearProperty(SECRET_KEY);
        System.clearProperty(PASSWORD_KEY);
        System.clearProperty(CREDENTIALS_KEY);
        System.clearProperty(API_KEY);
    }
}
