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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
          @ApiResponse(responseCode = "400", description = "Invalid URL or remote path"),
          @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, params = "remote")
  @ResponseStatus(HttpStatus.CREATED)
  public ImportResult importFromUrl(@RequestParam String remote, @RequestBody ImportFromUrlRequest body) {
    try {
      var downloaded = urlDownloadService.fetch(body.url());

      TestPayload payload = testPayloadReader.read(downloaded.bytes());

      if (payload.uid() == null || payload.uid().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uid is required");
      }
      if (payload.dtstamp() == null || payload.dtstamp().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dtstamp is required");
      }
      if (payload.dtstart() == null || payload.dtstart().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dtstart is required");
      }
      if (payload.dtend() == null || payload.dtend().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dtend is required");
      }

      var event = testPayloadReader.asEvent(payload);

      String sql = sqlScriptService.generate(event);
      byte[] sqlBytes = sql.getBytes(java.nio.charset.StandardCharsets.UTF_8);

      String path = sqlScriptService.deriveSqlRemote(remote);
      String fileName = path.substring(path.lastIndexOf('/') + 1);

      BucketUploadResponse uploadResponse =
              sqlScriptTransferClient.sendSqlScript(path, fileName, sqlBytes);

      return new ImportResult(
              remote,
              downloaded.bytes().length,
              payload.uid(),
              payload.dtstart(),
              payload.dtend(),
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

  public record ImportResult(
          String remote,
          int sizeBytes,
          String uid,
          String dtstart,
          String dtend,
          String bucketRemote,
          String shareUrl,
          long expirationTime
  ) {}
}