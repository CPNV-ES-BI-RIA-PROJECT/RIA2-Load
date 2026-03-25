package com.load.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.lang.reflect.Field;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientConfigTest {

    @Test
    void shouldCreateRestClientUsingConfiguredBaseUrl() throws Exception {
        /*
         * Feature: Bucket adapter HTTP client configuration
         * Scenario: Creating a RestClient bound to the configured base URL
         * Given the bucket adapter is exposed under a known base URL
         * When the configuration creates the HTTP client bean
         * Then the client resolves relative paths against that configured base URL
         */
        HttpClientConfig config = new HttpClientConfig();
        RestClient client = config.bucketAdapterRestClient("https://bucket.example.com/api");

        Field uriBuilderFactoryField = client.getClass().getDeclaredField("uriBuilderFactory");
        uriBuilderFactoryField.setAccessible(true);
        DefaultUriBuilderFactory uriBuilderFactory =
                (DefaultUriBuilderFactory) uriBuilderFactoryField.get(client);
        URI expandedUri = uriBuilderFactory.expand("/v1/ping");

        assertTrue(uriBuilderFactory.hasBaseUri());
        assertEquals(URI.create("https://bucket.example.com/api/v1/ping"), expandedUri);
    }
}
