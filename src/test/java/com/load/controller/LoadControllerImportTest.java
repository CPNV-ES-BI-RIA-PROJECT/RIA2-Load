package com.load.controller;

import com.load.dto.BucketUploadResponse;
import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import com.load.service.TestPayloadReader;
import com.load.service.UrlDownloadService;
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

  private MockMvc mockMvcFor(
          UrlDownloadService urlDownloadService,
          TestPayloadReader testPayloadReader,
          SqlScriptService sqlScriptService,
          SqlScriptTransferClient sqlScriptTransferClient
  ) {
    return MockMvcBuilders.standaloneSetup(new LoadController(
            urlDownloadService,
            testPayloadReader,
            sqlScriptService,
            sqlScriptTransferClient
    )).build();
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
