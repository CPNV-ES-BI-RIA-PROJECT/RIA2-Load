package com.load.service.importer;

import com.load.dto.BucketUploadResponse;
import com.load.dto.LoadImportResult;
import com.load.dto.TestPayload;
import com.load.service.TestPayloadReader;
import com.load.service.UrlDownloadService;
import com.load.service.sql.SqlScriptService;
import com.load.service.sql.SqlScriptTransferClient;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class LoadImportService {

    private final UrlDownloadService urlDownloadService;
    private final TestPayloadReader testPayloadReader;
    private final SqlScriptService sqlScriptService;
    private final SqlScriptTransferClient sqlScriptTransferClient;

    public LoadImportService(
            UrlDownloadService urlDownloadService,
            TestPayloadReader testPayloadReader,
            SqlScriptService sqlScriptService,
            SqlScriptTransferClient sqlScriptTransferClient
    ) {
        this.urlDownloadService = urlDownloadService;
        this.testPayloadReader = testPayloadReader;
        this.sqlScriptService = sqlScriptService;
        this.sqlScriptTransferClient = sqlScriptTransferClient;
    }

    public LoadImportResult importFromUrl(String remote, String url) {
        var downloaded = urlDownloadService.fetch(url);

        TestPayload payload = testPayloadReader.read(downloaded.bytes());
        validatePayload(payload);

        var event = testPayloadReader.asEvent(payload);

        String sql = sqlScriptService.generate(event);
        byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);

        String sqlRemote = sqlScriptService.deriveSqlRemote(remote);
        String fileName = sqlRemote.substring(sqlRemote.lastIndexOf('/') + 1);

        BucketUploadResponse uploadResponse =
                sqlScriptTransferClient.sendSqlScript(sqlRemote, fileName, sqlBytes);

        return new LoadImportResult(
                sqlRemote,
                downloaded.bytes().length,
                payload.uid(),
                payload.dtstart(),
                payload.dtend(),
                uploadResponse.remote(),
                uploadResponse.shareUrl(),
                uploadResponse.expirationTime()
        );
    }

    private static void validatePayload(TestPayload payload) {
        requireField(payload.uid(), "uid");
        requireField(payload.dtstamp(), "dtstamp");
        requireField(payload.dtstart(), "dtstart");
        requireField(payload.dtend(), "dtend");
    }

    private static void requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
