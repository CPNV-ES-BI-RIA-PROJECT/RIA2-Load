package com.load.service.sql;

import com.load.dto.BucketUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SqlScriptTransferClientTest {

    private MockRestServiceServer server;
    private SqlScriptTransferClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bucket.example.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SqlScriptTransferClient(builder.build());
    }

    @Test
    void shouldBuildMultipartUploadRequestAndReturnBucketResponse() throws Exception {
        /*
         * Feature: SQL script transfer
         * Scenario: Uploading a generated SQL script
         * Given a generated SQL script for Alice with a remote destination containing special characters
         * When the transfer client sends the SQL script to the bucket adapter
         * Then it builds the expected multipart request and returns the adapter response
         */
        byte[] content = "INSERT INTO events VALUES ('alice');".getBytes(StandardCharsets.UTF_8);
        BucketUploadResponse expectedResponse = new BucketUploadResponse(
                "shared/alice-report.sql",
                "https://bucket.example.com/shared/alice-report.sql",
                1700000000L
        );

        server.expect(request -> assertUploadRequest(
                request,
                "shared/alice report #1.sql",
                "alice-report.sql",
                content,
                true
        )).andRespond(withSuccess("""
                {
                  "remote": "shared/alice-report.sql",
                  "shareUrl": "https://bucket.example.com/shared/alice-report.sql",
                  "expirationTime": 1700000000
                }
                """, MediaType.APPLICATION_JSON));

        BucketUploadResponse actualResponse = client.sendSqlScript(
                "shared/alice report #1.sql",
                "alice-report.sql",
                content
        );

        server.verify();
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldAllowEmptyRemoteFilenameAndContent() throws Exception {
        /*
         * Feature: SQL script transfer
         * Scenario: Uploading an empty SQL artifact
         * Given an empty remote, an empty filename and an empty SQL content
         * When the transfer client sends the SQL script
         * Then it still creates a valid multipart request with empty values
         */
        server.expect(request -> assertUploadRequest(
                request,
                "",
                "",
                new byte[0],
                true
        )).andRespond(withSuccess("""
                {
                  "remote": "",
                  "shareUrl": "https://bucket.example.com/empty.sql",
                  "expirationTime": 0
                }
                """, MediaType.APPLICATION_JSON));

        BucketUploadResponse response = client.sendSqlScript("", "", new byte[0]);

        server.verify();
        assertEquals("", response.remote());
        assertEquals("https://bucket.example.com/empty.sql", response.shareUrl());
        assertEquals(0L, response.expirationTime());
    }

    @Test
    void shouldKeepNullFilenameOutOfMultipartMetadata() throws Exception {
        /*
         * Feature: SQL script transfer
         * Scenario: Uploading a SQL script without a filename
         * Given a generated SQL script whose filename is unknown
         * When the transfer client sends the SQL script
         * Then the multipart body keeps the filename unset instead of fabricating one
         */
        byte[] content = "SELECT 1;".getBytes(StandardCharsets.UTF_8);

        server.expect(request -> assertUploadRequest(
                request,
                "shared/unnamed.sql",
                null,
                content,
                false
        )).andRespond(withSuccess("""
                {
                  "remote": "shared/unnamed.sql",
                  "shareUrl": "https://bucket.example.com/shared/unnamed.sql",
                  "expirationTime": 1
                }
                """, MediaType.APPLICATION_JSON));

        BucketUploadResponse response = client.sendSqlScript("shared/unnamed.sql", null, content);

        server.verify();
        assertEquals("shared/unnamed.sql", response.remote());
    }

    @Test
    void shouldPropagateBucketAdapterFailure() {
        /*
         * Feature: SQL script transfer
         * Scenario: Failing to upload to the bucket adapter
         * Given the bucket adapter rejects the upload
         * When the transfer client sends the SQL script
         * Then the transfer client propagates the adapter failure
         */
        IOException adapterFailure = new IOException("adapter unavailable");
        server.expect(request -> assertEquals(HttpMethod.POST, request.getMethod()))
                .andRespond(withException(adapterFailure));

        ResourceAccessException failure = assertThrows(
                ResourceAccessException.class,
                () -> client.sendSqlScript("shared/failure.sql", "failure.sql", new byte[]{9})
        );

        server.verify();
        assertSame(adapterFailure, failure.getCause());
    }

    @Test
    void shouldRejectNullContentBeforeCallingRestClient() {
        /*
         * Feature: SQL script transfer
         * Scenario: Rejecting a missing SQL payload
         * Given a remote and filename but no SQL content
         * When the transfer client sends the SQL script
         * Then it fails fast before any HTTP request is created
         */
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> client.sendSqlScript("shared/missing.sql", "missing.sql", null)
        );

        assertEquals("Byte array must not be null", failure.getMessage());
    }

    private void assertUploadRequest(
            org.springframework.http.client.ClientHttpRequest request,
            String remote,
            String fileName,
            byte[] expectedContent,
            boolean filenameExpected
    ) throws IOException {
        assertEquals(HttpMethod.POST, request.getMethod());
        assertEquals("/api/v1/objects", request.getURI().getPath());
        assertEquals(remote, remoteQueryValue(request.getURI()));
        assertEquals(MediaType.APPLICATION_JSON, request.getHeaders().getAccept().getFirst());
        assertTrue(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA));

        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        MultipartPayload payload = multipartPayload(mockRequest);

        assertTrue(payload.headers().contains("Content-Disposition: form-data; name=\"file\""));
        assertTrue(payload.headers().contains("Content-Type: application/octet-stream"));

        if (filenameExpected) {
            assertTrue(payload.headers().contains("filename=\"" + fileName + "\""));
        } else {
            assertFalse(payload.headers().contains("filename="));
        }

        assertArrayEquals(expectedContent, payload.content());
    }

    private String remoteQueryValue(URI uri) {
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("remote");
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private MultipartPayload multipartPayload(MockClientHttpRequest request) {
        String boundary = request.getHeaders().getContentType().getParameter("boundary");
        String body = request.getBodyAsString(StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;

        int firstLineBreak = body.indexOf("\r\n");
        int headersStart = firstLineBreak + 2;
        int headersEnd = body.indexOf("\r\n\r\n", headersStart);
        int contentStart = headersEnd + 4;
        int contentEnd = body.indexOf("\r\n" + delimiter, contentStart);

        String headers = body.substring(headersStart, headersEnd);
        byte[] content = body.substring(contentStart, contentEnd).getBytes(StandardCharsets.ISO_8859_1);
        return new MultipartPayload(headers, content);
    }

    private record MultipartPayload(String headers, byte[] content) {
    }
}
