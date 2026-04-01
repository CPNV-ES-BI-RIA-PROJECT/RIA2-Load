package com.load.service;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlDownloadServiceTest {

    @Test
    void shouldDownloadRemoteObjectAndUseDefaultContentTypeWhenMissing() {
        /*
         * Feature: Remote object download
         * Scenario: Downloading a valid public object without an explicit content type
         * Given Alice shares a public HTTP resource with a successful response
         * When the downloader fetches the object
         * Then it returns the response bytes, falls back to the default content type and sends the expected request
         */
        byte[] expectedBytes = "payload".getBytes();
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                200,
                Map.of(),
                new ByteArrayInputStream(expectedBytes)
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        UrlDownloadService.DownloadedObject downloadedObject = service.fetch("http://8.8.8.8/files/alice.json");

        assertArrayEquals(expectedBytes, downloadedObject.bytes());
        assertEquals("application/octet-stream", downloadedObject.contentType());
        assertEquals("GET", client.lastRequest.method());
        assertEquals(URI.create("http://8.8.8.8/files/alice.json"), client.lastRequest.uri());
        assertEquals("ria2-load-downloader/1.0", client.lastRequest.headers().firstValue("User-Agent").orElseThrow());
        assertEquals(Duration.ofSeconds(60), client.lastRequest.timeout().orElseThrow());
    }

    @Test
    void shouldPreserveProvidedContentTypeWhenDownloadSucceeds() {
        /*
         * Feature: Remote object download
         * Scenario: Preserving the remote content type on a successful download
         * Given Bob shares a public JSON resource with a declared content type
         * When the downloader fetches the object
         * Then the returned downloaded object keeps that content type
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                200,
                Map.of("content-type", List.of("application/json")),
                new ByteArrayInputStream("{\"uid\":\"evt-123\"}".getBytes())
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        UrlDownloadService.DownloadedObject downloadedObject = service.fetch("http://8.8.8.8/files/data.json");

        assertEquals("application/json", downloadedObject.contentType());
    }

    @Test
    void shouldRejectUnexpectedHttpStatus() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting a remote response with an unexpected status
         * Given the remote server answers with a non-success status
         * When the downloader fetches the object
         * Then it fails with a business-facing status error
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                404,
                Map.of(),
                new ByteArrayInputStream(new byte[0])
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/missing.json")
        );

        assertEquals("Unexpected HTTP status: 404", failure.getMessage());
    }

    @Test
    void shouldRejectResponseWhoseContentLengthExceedsMaximum() {
        /*
         * Feature: Remote object download
         * Scenario: Refusing a remote object announced as too large
         * Given the remote server declares a payload larger than the allowed limit
         * When the downloader fetches the object
         * Then it aborts before reading the body
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                200,
                Map.of("content-length", List.of(String.valueOf(50L * 1024 * 1024 + 1))),
                new ByteArrayInputStream(new byte[0])
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/large.bin")
        );

        assertEquals("File too large (Content-Length=52428801)", failure.getMessage());
    }

    @Test
    void shouldRejectStreamThatExceedsMaximumSizeWhileReading() {
        /*
         * Feature: Remote object download
         * Scenario: Refusing a payload that grows beyond the allowed size during streaming
         * Given the remote server omits the content length but streams more than the allowed limit
         * When the downloader fetches the object
         * Then it aborts with a max-size error while reading
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                200,
                Map.of(),
                new RepeatingInputStream(50L * 1024 * 1024 + 1)
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/streamed.bin")
        );

        assertEquals("File exceeds max size (52428800 bytes)", failure.getMessage());
    }

    @Test
    void shouldWrapIoErrorsDuringDownload() {
        /*
         * Feature: Remote object download
         * Scenario: Propagating I/O failures from the remote call
         * Given the HTTP client cannot read the remote resource
         * When the downloader fetches the object
         * Then it raises a download I/O error with the original cause
         */
        IOException transportFailure = new IOException("network down");
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> {
            throw transportFailure;
        });
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/files/data.json")
        );

        assertEquals("I/O error while downloading", failure.getMessage());
        assertEquals(transportFailure, failure.getCause());
    }

    @Test
    void shouldWrapIoErrorsWhileReadingResponseBody() {
        /*
         * Feature: Remote object download
         * Scenario: Propagating I/O failures while consuming the response stream
         * Given the remote server starts responding but the body stream breaks
         * When the downloader fetches the object
         * Then it raises a download I/O error with the original cause
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> response(
                request,
                200,
                Map.of(),
                new FailingInputStream()
        ));
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/files/broken.json")
        );

        assertEquals("I/O error while downloading", failure.getMessage());
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals("broken stream", failure.getCause().getMessage());
    }

    @Test
    void shouldRestoreInterruptedFlagWhenDownloadIsInterrupted() {
        /*
         * Feature: Remote object download
         * Scenario: Preserving the interrupted status when the HTTP call is interrupted
         * Given the thread is interrupted while waiting for the remote response
         * When the downloader fetches the object
         * Then it fails with an interruption error and restores the thread interrupted flag
         */
        StubHttpClient client = new StubHttpClient((request, bodyHandler) -> {
            throw new InterruptedException("stop now");
        });
        UrlDownloadService service = new UrlDownloadService(client);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.fetch("http://8.8.8.8/files/data.json")
        );

        assertEquals("Download interrupted", failure.getMessage());
        assertInstanceOf(InterruptedException.class, failure.getCause());
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(Thread.interrupted());
    }

    @Test
    void shouldRejectMalformedUrl() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting a malformed URL string
         * Given the caller provides an invalid URL
         * When the downloader fetches the object
         * Then it fails with an invalid URL error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://[broken")
        );

        assertEquals("Invalid URL", failure.getMessage());
    }

    @Test
    void shouldRejectUnsupportedUrlScheme() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting non-http URLs
         * Given the caller provides a URL with an unsupported scheme
         * When the downloader fetches the object
         * Then it fails with a scheme validation error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("ftp://8.8.8.8/data.json")
        );

        assertEquals("Only http/https URLs are allowed", failure.getMessage());
    }

    @Test
    void shouldRejectUrlWithoutScheme() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting URL values that omit the scheme
         * Given the caller provides a host without http or https
         * When the downloader fetches the object
         * Then it fails with a scheme validation error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("//8.8.8.8/data.json")
        );

        assertEquals("Only http/https URLs are allowed", failure.getMessage());
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting URLs without a host
         * Given the caller provides an HTTP URL that does not identify a host
         * When the downloader fetches the object
         * Then it fails with a host validation error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http:///missing-host.json")
        );

        assertEquals("URL host is required", failure.getMessage());
    }

    @Test
    void shouldRejectLoopbackAndPrivateAddresses() {
        /*
         * Feature: Remote object download
         * Scenario: Blocking loopback and private network targets
         * Given the caller targets a local or private address
         * When the downloader fetches the object
         * Then it rejects the request to reduce SSRF risk
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException loopbackFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://127.0.0.1/data.json")
        );
        IllegalArgumentException privateFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://192.168.1.10/data.json")
        );

        assertEquals("Local/private addresses are not allowed", loopbackFailure.getMessage());
        assertEquals("Local/private addresses are not allowed", privateFailure.getMessage());
    }

    @Test
    void shouldRejectAwsMetadataAddressExplicitly() {
        /*
         * Feature: Remote object download
         * Scenario: Blocking the AWS metadata endpoint explicitly
         * Given the caller targets the well-known metadata IP address
         * When the downloader fetches the object
         * Then it rejects the request with the metadata-specific validation error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://169.254.169.254/latest/meta-data")
        );

        assertEquals("Metadata address is not allowed", failure.getMessage());
    }

    @Test
    void shouldRejectUnresolvableHosts() {
        /*
         * Feature: Remote object download
         * Scenario: Rejecting a host that cannot be resolved safely
         * Given the caller targets a host that cannot be resolved
         * When the downloader fetches the object
         * Then it fails with a DNS resolution error
         */
        UrlDownloadService service = new UrlDownloadService();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://definitely-invalid-hostname.invalid/data.json")
        );

        assertEquals("Unable to resolve host", failure.getMessage());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void shouldDefensivelyCopyDownloadedObjectBytes() {
        /*
         * Feature: Downloaded object immutability
         * Scenario: Protecting the downloaded bytes from external mutation
         * Given the downloaded object is created from a mutable byte array
         * When callers mutate the original array or the returned array
         * Then the downloaded object keeps its own internal copy
         */
        byte[] source = {1, 2, 3};

        UrlDownloadService.DownloadedObject downloadedObject =
                new UrlDownloadService.DownloadedObject(source, "application/json");
        source[0] = 9;

        byte[] firstRead = downloadedObject.bytes();
        firstRead[1] = 8;

        assertArrayEquals(new byte[]{1, 2, 3}, downloadedObject.bytes());
        assertEquals("application/json", downloadedObject.contentType());
    }

    @Test
    void shouldAllowNullBytesInDownloadedObject() {
        /*
         * Feature: Downloaded object immutability
         * Scenario: Preserving a missing byte payload
         * Given the downloaded object is created with null bytes
         * When callers read the bytes
         * Then the result stays null without failing
         */
        UrlDownloadService.DownloadedObject downloadedObject =
                new UrlDownloadService.DownloadedObject(null, "application/octet-stream");

        assertEquals(null, downloadedObject.bytes());
        assertEquals("application/octet-stream", downloadedObject.contentType());
    }

    private static HttpResponse<InputStream> response(
            HttpRequest request,
            int statusCode,
            Map<String, List<String>> headers,
            InputStream body
    ) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (left, right) -> true);
            }

            @Override
            public InputStream body() {
                return body;
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    @FunctionalInterface
    private interface SendBehavior {
        HttpResponse<InputStream> send(HttpRequest request, HttpResponse.BodyHandler<InputStream> bodyHandler)
                throws IOException, InterruptedException;
    }

    private static final class StubHttpClient extends HttpClient {

        private final SendBehavior sendBehavior;
        private HttpRequest lastRequest;

        private StubHttpClient(SendBehavior sendBehavior) {
            this.sendBehavior = sendBehavior;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            lastRequest = request;
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) sendBehavior.send(
                    request,
                    (HttpResponse.BodyHandler<InputStream>) responseBodyHandler
            );
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailingInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("broken stream");
        }
    }

    private static final class RepeatingInputStream extends InputStream {

        private long remainingBytes;

        private RepeatingInputStream(long remainingBytes) {
            this.remainingBytes = remainingBytes;
        }

        @Override
        public int read(byte[] buffer, int off, int len) {
            if (remainingBytes == 0) {
                return -1;
            }

            int read = (int) Math.min(len, remainingBytes);
            for (int index = 0; index < read; index++) {
                buffer[off + index] = 'a';
            }
            remainingBytes -= read;
            return read;
        }

        @Override
        public int read() {
            if (remainingBytes == 0) {
                return -1;
            }

            remainingBytes--;
            return 'a';
        }
    }
}
