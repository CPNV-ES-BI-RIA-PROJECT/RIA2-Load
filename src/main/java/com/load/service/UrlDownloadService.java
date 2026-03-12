package com.load.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class UrlDownloadService {

    // MVP: limite simple. Idéalement configurable via properties.
    private static final long MAX_BYTES = 50L * 1024 * 1024; // 50 MB

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DownloadedObject fetch(String url) {
        URI uri = validateUrl(url);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .header("User-Agent", "ria2-load-downloader/1.0")
                .build();

        try {
            HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Unexpected HTTP status: " + resp.statusCode());
            }

            long contentLength = resp.headers().firstValueAsLong("content-length").orElse(-1);
            if (contentLength > MAX_BYTES) {
                throw new IllegalStateException("File too large (Content-Length=" + contentLength + ")");
            }

            String contentType = resp.headers().firstValue("content-type").orElse("application/octet-stream");

            byte[] bytes = readWithLimit(resp.body(), MAX_BYTES);
            return new DownloadedObject(bytes, contentType);

        } catch (IOException e) {
            throw new IllegalStateException("I/O error while downloading", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download interrupted", e);
        }
    }

    private static byte[] readWithLimit(InputStream in, long maxBytes) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;

            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException("File exceeds max size (" + maxBytes + " bytes)");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static URI validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL host is required");
        }

        // SSRF basic guard (MVP). Pas parfait contre DNS rebinding, mais utile.
        blockLocalAddresses(uri.getHost());

        return uri;
    }

    private static void blockLocalAddresses(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()) {
                throw new IllegalArgumentException("Local/private addresses are not allowed");
            }
            // Bloquer metadata AWS courante (si résolue par IP directe)
            if ("169.254.169.254".equals(addr.getHostAddress())) {
                throw new IllegalArgumentException("Metadata address is not allowed");
            }
        } catch (IOException e) {
            // Si résolution DNS échoue: on rejette (plus sûr)
            throw new IllegalArgumentException("Unable to resolve host", e);
        }
    }

    public record DownloadedObject(byte[] bytes, String contentType) {

        public DownloadedObject {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
