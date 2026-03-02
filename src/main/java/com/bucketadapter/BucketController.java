package com.bucketadapter;

import com.bucketadapter.dto.TestPayload;
import com.bucketadapter.service.TestPayloadReader;
import com.bucketadapter.service.UrlDownloadService;
import com.bucketadapter.service.sql.SqlScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.bucketadapter.dto.Rows.CustomerRow;
import com.bucketadapter.dto.Rows.OrderItemRow;
import com.bucketadapter.dto.Rows.OrderRow;


import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.List;

import com.bucketadapter.dto.ImportFromUrlRequest;

@RestController
@RequestMapping("/load/objects")
public class BucketController {

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "BucketService is injected by Spring (bean); the controller does not expose it.")
  private final BucketService bucketService;
  private final UrlDownloadService urlDownloadService;
  private final TestPayloadReader testPayloadReader;
  private final SqlScriptService sqlScriptService;

  public BucketController(
          BucketService bucketService,
          UrlDownloadService urlDownloadService,
          TestPayloadReader testPayloadReader,
          SqlScriptService sqlScriptService
  ) {
    this.bucketService = bucketService;
    this.urlDownloadService = urlDownloadService;
    this.testPayloadReader = testPayloadReader;
    this.sqlScriptService = sqlScriptService;
  }


  @Operation(
      summary = "List objects",
      description =
          "Lists objects under the remote prefix. Use recursive=true to include subfolders.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List returned"),
    @ApiResponse(responseCode = "400", description = "Invalid remote path"),
    @ApiResponse(responseCode = "404", description = "Bucket/object not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @GetMapping(params = "remote")
  public List<String> list(
      @RequestParam String remote, @RequestParam(defaultValue = "false") boolean recursive) {
    return bucketService.list(remote, recursive);
  }

  @Operation(summary = "Upload an object")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Object created"),
    @ApiResponse(responseCode = "400", description = "Invalid remote path"),
    @ApiResponse(responseCode = "404", description = "Bucket/object not found")
  })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public void upload(@RequestParam String remote, @RequestPart("file") MultipartFile file) {
    try {
      bucketService.upload(remote, file.getBytes());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file", e);
    }
  }

  @Operation(
      summary = "Delete an object",
      description =
          "Deletes the object targeted by remote. If remote points to a prefix, use recursive=true.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Object deleted"),
    @ApiResponse(responseCode = "400", description = "Invalid remote path"),
    @ApiResponse(responseCode = "404", description = "Bucket/object not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @DeleteMapping(params = "remote")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestParam String remote, @RequestParam(defaultValue = "false") boolean recursive) {
    bucketService.delete(remote, recursive);
  }

  @Operation(
      summary = "Share an object",
      description =
          "Returns a presigned URL for the remote object, valid for expirationTime seconds.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Presigned URL returned"),
    @ApiResponse(responseCode = "400", description = "Invalid remote path"),
    @ApiResponse(responseCode = "404", description = "Bucket/object not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @GetMapping(
      value = "/share",
      params = {"remote", "expirationTime"})
  public String share(@RequestParam String remote, @RequestParam int expirationTime) {
    return bucketService.share(remote, expirationTime);
  }

  private static final org.slf4j.Logger log =
          org.slf4j.LoggerFactory.getLogger(BucketController.class);

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

      // Generation of SQL command
      String sql = sqlScriptService.generate(customers, orders, orderItems);
      byte[] sqlBytes = sql.getBytes(java.nio.charset.StandardCharsets.UTF_8);

      // Storage JSON file
      bucketService.upload(remote, downloaded.bytes());

      // Storage SQL file
      String sqlRemote = SqlScriptService.deriveSqlRemote(remote);
      bucketService.upload(sqlRemote, sqlBytes);

      return new ImportResult(
              remote,
              downloaded.bytes().length,
              payload.schemaVersion(),
              payload.businessDate().toString()
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

  public record ImportResult(String remote, int sizeBytes, String schemaVersion, String businessDate) {}

}
