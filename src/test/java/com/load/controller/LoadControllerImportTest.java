package com.load.controller;

import com.load.dto.BucketUploadResponse;
import com.load.dto.LoadImportResult;
import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import com.load.service.TestPayloadReader;
import com.load.service.UrlDownloadService;
import com.load.service.importer.LoadImportService;
import com.load.service.sql.SqlScriptService;
import com.load.service.sql.SqlScriptTransferClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoadControllerImportTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = mockMvcFor(new UrlDownloadService() {
      @Override
      public DownloadedObject fetch(String url) {
        throw new IllegalStateException("I/O error while downloading");
      }
    }, null, null, null);
  }

  @Test
  void shouldReturnBadGatewayWhenRemoteContentIsInaccessible() throws Exception {
    mockMvc.perform(post("/load/objects/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"url":"https://example.com/missing.json"}
                            """))
            .andExpect(status().isBadGateway())
            .andExpect(result -> {
              Exception exception = result.getResolvedException();
              ResponseStatusException responseStatusException =
                      assertInstanceOf(ResponseStatusException.class, exception);
              assertEquals(HttpStatus.BAD_GATEWAY, responseStatusException.getStatusCode());
            });
  }

  @Test
  void shouldReturnCreatedWhenObjectImportSucceeds() throws Exception {
    /*
     * Feature: Object import from a shared URL
     * Scenario: Importing a valid payload and publishing its SQL script
     * Given Alice shares a valid remote payload
     * When the import endpoint processes the request
     * Then it downloads the payload, generates SQL, uploads the script and returns the import summary
     */
    byte[] downloadedBytes = """
            {"uid":"evt-123"}
            """.getBytes(StandardCharsets.UTF_8);
    TestPayload payload = new TestPayload(
            "evt-123",
            "20260109T100000Z",
            "20260109T110000Z",
            "20260109T120000Z",
            "Load test",
            "Imported event",
            "training",
            "organizer@example.com",
            "attendee@example.com",
            "Lausanne",
            "Europe/Zurich"
    );
    EventRow event = new EventRow(
            "evt-123",
            "2026-01-09 10:00:00",
            "2026-01-09 11:00:00",
            "2026-01-09 12:00:00",
            "Load test",
            "Imported event",
            "training",
            "organizer@example.com",
            "attendee@example.com",
            "Lausanne",
            "Europe/Zurich"
    );
    RecordingSqlScriptService sqlScriptService = new RecordingSqlScriptService(
            "INSERT INTO events VALUES ('evt-123');",
            "bi1-julien/load/test.sql"
    );
    RecordingSqlScriptTransferClient sqlScriptTransferClient = new RecordingSqlScriptTransferClient(
            new BucketUploadResponse(
                    "bucket/test.sql",
                    "https://bucket.example.com/test.sql",
                    1700000000L
            )
    );

    mockMvc = mockMvcFor(
            new StaticUrlDownloadService(downloadedBytes),
            new StubTestPayloadReader(payload, event),
            sqlScriptService,
            sqlScriptTransferClient
    );

    mockMvc.perform(post("/load/objects/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"url":"https://example.com/object.json"}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.remote").value("bi1-julien/load/test.sql"))
            .andExpect(jsonPath("$.sizeBytes").value(downloadedBytes.length))
            .andExpect(jsonPath("$.uid").value("evt-123"))
            .andExpect(jsonPath("$.dtstart").value("20260109T110000Z"))
            .andExpect(jsonPath("$.dtend").value("20260109T120000Z"))
            .andExpect(jsonPath("$.bucketRemote").value("bucket/test.sql"))
            .andExpect(jsonPath("$.shareUrl").value("https://bucket.example.com/test.sql"))
            .andExpect(jsonPath("$.expirationTime").value(1700000000));

    assertEquals(event, sqlScriptService.generatedEvent);
    assertEquals("bi1-julien/load/test.sql", sqlScriptTransferClient.remote);
    assertEquals("test.sql", sqlScriptTransferClient.fileName);
    assertArrayEquals(
            "INSERT INTO events VALUES ('evt-123');".getBytes(StandardCharsets.UTF_8),
            sqlScriptTransferClient.content
    );
  }

  @Test
  void shouldReturnBadRequestWhenUidIsMissing() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload without a uid
     * Given the remote payload does not define a uid
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload(null, "20260109T100000Z", "20260109T110000Z", "20260109T120000Z"), "uid is required");
  }

  @Test
  void shouldReturnBadRequestWhenUidIsBlank() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload whose uid is blank
     * Given the remote payload defines a blank uid
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("   ", "20260109T100000Z", "20260109T110000Z", "20260109T120000Z"), "uid is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtstampIsMissing() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload without a dtstamp
     * Given the remote payload does not define a dtstamp
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", null, "20260109T110000Z", "20260109T120000Z"), "dtstamp is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtstampIsBlank() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload whose dtstamp is blank
     * Given the remote payload defines a blank dtstamp
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", "   ", "20260109T110000Z", "20260109T120000Z"), "dtstamp is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtstartIsMissing() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload without a dtstart
     * Given the remote payload does not define a dtstart
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", "20260109T100000Z", null, "20260109T120000Z"), "dtstart is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtstartIsBlank() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload whose dtstart is blank
     * Given the remote payload defines a blank dtstart
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", "20260109T100000Z", "   ", "20260109T120000Z"), "dtstart is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtendIsMissing() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload without a dtend
     * Given the remote payload does not define a dtend
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", "20260109T100000Z", "20260109T110000Z", null), "dtend is required");
  }

  @Test
  void shouldReturnBadRequestWhenDtendIsBlank() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a payload whose dtend is blank
     * Given the remote payload defines a blank dtend
     * When the import endpoint processes the request
     * Then it rejects the import with a bad request response
     */
    assertBadRequestForPayload(payload("evt-123", "20260109T100000Z", "20260109T110000Z", "   "), "dtend is required");
  }

  @Test
  void shouldReturnBadRequestWhenPayloadReaderRejectsDownloadedContent() throws Exception {
    /*
     * Feature: Object import validation
     * Scenario: Rejecting a downloaded payload that violates business rules
     * Given the payload reader refuses the downloaded content
     * When the import endpoint processes the request
     * Then it translates that validation failure into a bad request response
     */
    IllegalArgumentException invalidPayload = new IllegalArgumentException("Payload is invalid");

    mockMvc = mockMvcFor(
            new StaticUrlDownloadService("{}".getBytes(StandardCharsets.UTF_8)),
            new TestPayloadReader(null) {
              @Override
              public TestPayload read(byte[] bytes) {
                throw invalidPayload;
              }
            },
            new SqlScriptService(),
            new SqlScriptTransferClient(null)
    );

    mockMvc.perform(post("/load/objects/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"url":"https://example.com/object.json"}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(result -> {
              ResponseStatusException responseStatusException =
                      responseStatusException(result.getResolvedException());
              assertEquals(HttpStatus.BAD_REQUEST, responseStatusException.getStatusCode());
              assertEquals("Payload is invalid", responseStatusException.getReason());
              assertSame(invalidPayload, responseStatusException.getCause());
            });
  }

  @Test
  void shouldReturnInternalServerErrorWhenUploadFailsUnexpectedly() throws Exception {
    /*
     * Feature: Object import execution
     * Scenario: Failing unexpectedly after the payload is accepted
     * Given the generated SQL script cannot be uploaded because of an unexpected backend failure
     * When the import endpoint processes the request
     * Then it returns an internal server error response
     */
    RuntimeException uploadFailure = new RuntimeException("bucket adapter down");

    mockMvc = mockMvcFor(
            new StaticUrlDownloadService("{}".getBytes(StandardCharsets.UTF_8)),
            new StubTestPayloadReader(validPayload(), validEvent()),
            new RecordingSqlScriptService(
                    "INSERT INTO events VALUES ('evt-123');",
                    "bi1-julien/load/test.sql"
            ),
            new SqlScriptTransferClient(null) {
              @Override
              public BucketUploadResponse sendSqlScript(String remote, String fileName, byte[] content) {
                throw uploadFailure;
              }
            }
    );

    mockMvc.perform(post("/load/objects/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"url":"https://example.com/object.json"}
                            """))
            .andExpect(status().isInternalServerError())
            .andExpect(result -> {
              ResponseStatusException responseStatusException =
                      responseStatusException(result.getResolvedException());
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseStatusException.getStatusCode());
              assertEquals("Import failed", responseStatusException.getReason());
              assertSame(uploadFailure, responseStatusException.getCause());
            });
  }

  @Test
  void shouldExposeImportResultRecordValues() {
    /*
     * Feature: Import result representation
     * Scenario: Exposing the import summary as an immutable record
     * Given an import result is produced after a successful import
     * When its record accessors are read
     * Then each accessor returns the stored business value
     */
    LoadImportResult result = new LoadImportResult(
            "bi1-julien/load/test.sql",
            42,
            "evt-123",
            "20260109T110000Z",
            "20260109T120000Z",
            "bucket/test.sql",
            "https://bucket.example.com/test.sql",
            1700000000L
    );

    assertEquals("bi1-julien/load/test.sql", result.remote());
    assertEquals(42, result.sizeBytes());
    assertEquals("evt-123", result.uid());
    assertEquals("20260109T110000Z", result.dtstart());
    assertEquals("20260109T120000Z", result.dtend());
    assertEquals("bucket/test.sql", result.bucketRemote());
    assertEquals("https://bucket.example.com/test.sql", result.shareUrl());
    assertEquals(1700000000L, result.expirationTime());
  }

  private MockMvc mockMvcFor(
          UrlDownloadService urlDownloadService,
          TestPayloadReader testPayloadReader,
          SqlScriptService sqlScriptService,
          SqlScriptTransferClient sqlScriptTransferClient
  ) {
    LoadImportService loadImportService = new LoadImportService(
            urlDownloadService,
            testPayloadReader,
            sqlScriptService,
            sqlScriptTransferClient
    );
    return MockMvcBuilders.standaloneSetup(new LoadController(loadImportService)).build();
  }

  private void assertBadRequestForPayload(TestPayload payload, String expectedReason) throws Exception {
    mockMvc = mockMvcFor(
            new StaticUrlDownloadService("{}".getBytes(StandardCharsets.UTF_8)),
            new StubTestPayloadReader(payload, validEvent()),
            new RecordingSqlScriptService(
                    "INSERT INTO events VALUES ('evt-123');",
                    "bi1-julien/load/test.sql"
            ),
            new RecordingSqlScriptTransferClient(new BucketUploadResponse(
                    "bucket/test.sql",
                    "https://bucket.example.com/test.sql",
                    1700000000L
            ))
    );

    mockMvc.perform(post("/load/objects/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"url":"https://example.com/object.json"}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(result -> {
              ResponseStatusException responseStatusException =
                      responseStatusException(result.getResolvedException());
              assertEquals(HttpStatus.BAD_REQUEST, responseStatusException.getStatusCode());
              assertEquals(expectedReason, responseStatusException.getReason());
            });
  }

  private ResponseStatusException responseStatusException(Exception exception) {
    return assertInstanceOf(ResponseStatusException.class, exception);
  }

  private TestPayload validPayload() {
    return payload("evt-123", "20260109T100000Z", "20260109T110000Z", "20260109T120000Z");
  }

  private TestPayload payload(String uid, String dtstamp, String dtstart, String dtend) {
    return new TestPayload(
            uid,
            dtstamp,
            dtstart,
            dtend,
            "Load test",
            "Imported event",
            "training",
            "organizer@example.com",
            "attendee@example.com",
            "Lausanne",
            "Europe/Zurich"
    );
  }

  private EventRow validEvent() {
    return new EventRow(
            "evt-123",
            "2026-01-09 10:00:00",
            "2026-01-09 11:00:00",
            "2026-01-09 12:00:00",
            "Load test",
            "Imported event",
            "training",
            "organizer@example.com",
            "attendee@example.com",
            "Lausanne",
            "Europe/Zurich"
    );
  }

  private static final class StaticUrlDownloadService extends UrlDownloadService {

    private final byte[] bytes;

    private StaticUrlDownloadService(byte[] bytes) {
      this.bytes = bytes.clone();
    }

    @Override
    public DownloadedObject fetch(String url) {
      return new DownloadedObject(bytes, MediaType.APPLICATION_JSON_VALUE);
    }
  }

  private static final class StubTestPayloadReader extends TestPayloadReader {

    private final TestPayload payload;
    private final EventRow event;

    private StubTestPayloadReader(TestPayload payload, EventRow event) {
      super(null);
      this.payload = payload;
      this.event = event;
    }

    @Override
    public TestPayload read(byte[] bytes) {
      return payload;
    }

    @Override
    public EventRow asEvent(TestPayload payload) {
      return event;
    }
  }

  private static final class RecordingSqlScriptService extends SqlScriptService {

    private final String sql;
    private final String remotePath;
    private EventRow generatedEvent;

    private RecordingSqlScriptService(String sql, String remotePath) {
      this.sql = sql;
      this.remotePath = remotePath;
    }

    @Override
    public String generate(EventRow event) {
      generatedEvent = event;
      return sql;
    }

    @Override
    public String generateRemotePath() {
      return remotePath;
    }
  }

  private static final class RecordingSqlScriptTransferClient extends SqlScriptTransferClient {

    private final BucketUploadResponse response;
    private String remote;
    private String fileName;
    private byte[] content;

    private RecordingSqlScriptTransferClient(BucketUploadResponse response) {
      super(null);
      this.response = response;
    }

    @Override
    public BucketUploadResponse sendSqlScript(String remote, String fileName, byte[] content) {
      this.remote = remote;
      this.fileName = fileName;
      this.content = content.clone();
      return response;
    }
  }
}
