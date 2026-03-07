package com.load.service.sql;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class SqlScriptTransferClient {

    private final RestClient restClient;

    public SqlScriptTransferClient(RestClient bucketAdapterRestClient) {
        this.restClient = bucketAdapterRestClient;
    }

    public void sendSqlScript(String remote, String fileName, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentDisposition(
                ContentDisposition.formData()
                        .name("file")
                        .filename(fileName)
                        .build()
        );

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/objects")
                        .queryParam("remote", remote)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}