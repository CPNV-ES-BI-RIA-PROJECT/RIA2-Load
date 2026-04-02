package com.load.controller;

import com.load.dto.BucketUploadResponse;
import com.load.dto.ImportFromUrlRequest;
import com.load.dto.TestPayload;
import com.load.service.TestPayloadReader;
import com.load.service.UrlDownloadService;
import com.load.service.sql.SqlScriptService;
import com.load.service.sql.SqlScriptTransferClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/load/objects")
public class LoadController {

  private final UrlDownloadService urlDownloadService;
  private final TestPayloadReader testPayloadReader;
  private final SqlScriptService sqlScriptService;
  private final SqlScriptTransferClient sqlScriptTransferClient;

  public LoadController(
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

  private static final org.slf4j.Logger log =
          org.slf4j.LoggerFactory.getLogger(LoadController.class);

  @Operation(
          summary = "Import an object from a shared URL",
          description = "Downloads a file from an HTTP(S) URL, reads JSON, translates it into SQL and stores the script in bucket."
  )
  @ApiResponses({
          @ApiResponse(responseCode = "201", description = "Object imported"),
          @ApiResponse(responseCode = "400", description = "Invalid URL"),
          @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ImportResult importFromUrl(@RequestBody ImportFromUrlRequest body) {
    try {
      final var downloaded = fetchRemoteObject(body.url());

      List<TestPayload> payloads = testPayloadReader.readAll(downloaded.bytes());
      validatePayloads(payloads);

      var events = testPayloadReader.asEvents(payloads);

      String sql = sqlScriptService.generate(events);
      byte[] sqlBytes = sql.getBytes(java.nio.charset.StandardCharsets.UTF_8);

      String path = sqlScriptService.generateRemotePath();
      String fileName = path.substring(path.lastIndexOf('/') + 1);

      BucketUploadResponse uploadResponse =
              sqlScriptTransferClient.sendSqlScript(path, fileName, sqlBytes);

      TestPayload firstPayload = payloads.getFirst();
      return new ImportResult(
              path,
              downloaded.bytes().length,
              payloads.size(),
              firstPayload.uid(),
              firstPayload.dtstart(),
              firstPayload.dtend(),
              uploadResponse.remote(),
              uploadResponse.shareUrl(),
              uploadResponse.expirationTime()
      );

    } catch (ResponseStatusException e) {
      log.error("Import failed with status={}", e.getStatusCode(), e);
      throw e;
    } catch (IllegalArgumentException e) {
      log.error("Import bad request", e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Import unexpected error", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Import failed", e);
    }
  }

  private UrlDownloadService.DownloadedObject fetchRemoteObject(String url) {
    try {
      return urlDownloadService.fetch(url);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY,
              "Remote resource is inaccessible",
              e
      );
    }
  }

  private void validatePayloads(List<TestPayload> payloads) {
    for (int i = 0; i < payloads.size(); i++) {
      TestPayload payload = payloads.get(i);
      String prefix = payloads.size() == 1 ? "" : "events[" + i + "].";

      requireValue(payload.uid(), prefix + "uid");
      requireValue(payload.dtstamp(), prefix + "dtstamp");
      requireValue(payload.dtstart(), prefix + "dtstart");
      requireValue(payload.dtend(), prefix + "dtend");
    }
  }

  private void requireValue(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
    }
  }

  public record ImportResult(
          String remote,
          int sizeBytes,
          int eventCount,
          String uid,
          String dtstart,
          String dtend,
          String bucketRemote,
          String shareUrl,
          long expirationTime
  ) {}
}
