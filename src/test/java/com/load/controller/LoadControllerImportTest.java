package com.load.controller;

import com.load.service.UrlDownloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoadControllerImportTest {

  private UrlDownloadService urlDownloadService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    urlDownloadService = new UrlDownloadService() {
      @Override
      public DownloadedObject fetch(String url) {
        throw new IllegalStateException("I/O error while downloading");
      }
    };

    LoadController controller = new LoadController(
            urlDownloadService,
            null,
            null,
            null
    );

    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}
