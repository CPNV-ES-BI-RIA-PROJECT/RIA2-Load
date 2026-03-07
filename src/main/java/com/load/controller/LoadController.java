package com.load.controller;

import com.load.dto.BucketUploadResponse;
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
import org.springframework.web.bind.annotation.*;
import com.load.dto.Rows.CustomerRow;
import com.load.dto.Rows.OrderItemRow;
import com.load.dto.Rows.OrderRow;


import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import com.load.dto.ImportFromUrlRequest;

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
          description = "Downloads a file from an HTTP(S) URL, read content to translate json into sql and store script in bucket.")
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

      // TODO Change with real data in week 5
      // Read JSON to dto
      TestPayload payload = testPayloadReader.read(downloaded.bytes());

      if (payload.businessDate() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "business_date is required");
      }
      if (payload.schemaVersion() == null || payload.schemaVersion().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schema_version is required");
      }

      // Get type of rows
      List<CustomerRow> customers = new ArrayList<>();
      List<OrderRow> orders = new ArrayList<>();
      List<OrderItemRow> orderItems = new ArrayList<>();

      for (var table : payload.tables()) {
        switch (table.name()) {
          case "customers" -> customers.addAll(testPayloadReader.asCustomers(table));
          case "orders" -> orders.addAll(testPayloadReader.asOrders(table));
          case "order_items" -> orderItems.addAll(testPayloadReader.asOrderItems(table));
          default -> {
            // MVP: Ignore empty tables
          }
        }
      }

      // TODO Change with real translation SQL in week 5
      // Generation of SQL command
      String sql = sqlScriptService.generate(customers, orders, orderItems);
      byte[] sqlBytes = sql.getBytes(java.nio.charset.StandardCharsets.UTF_8);

      String fileName = "import_%s_%s.sql".formatted(
              payload.schemaVersion(),
              payload.businessDate()
      );
      String path = "bi1-julien/" + fileName;

      BucketUploadResponse uploadResponse =
              sqlScriptTransferClient.sendSqlScript(path, fileName, sqlBytes);

      return new ImportResult(
              remote,
              downloaded.bytes().length,
              payload.schemaVersion(),
              payload.businessDate().toString(),
              uploadResponse.remote(),
              uploadResponse.shareUrl(),
              uploadResponse.expirationTime()
      );

    } catch (org.springframework.web.server.ResponseStatusException e) {
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
          String schemaVersion,
          String businessDate,
          String bucketRemote,
          String shareUrl,
          long expirationTime
  ) {}

}
