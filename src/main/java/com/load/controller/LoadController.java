package com.load.controller;

import com.load.dto.ImportFromUrlRequest;
import com.load.dto.LoadImportResult;
import com.load.service.importer.LoadImportService;
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

  private final LoadImportService loadImportService;

  public LoadController(
          LoadImportService loadImportService
  ) {
    this.loadImportService = loadImportService;
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
      LoadImportResult result = loadImportService.importFromUrl(remote, body.url());

      return new ImportResult(
              result.remote(),
              result.sizeBytes(),
              result.uid(),
              result.dtstart(),
              result.dtend(),
              result.bucketRemote(),
              result.shareUrl(),
              result.expirationTime()
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
