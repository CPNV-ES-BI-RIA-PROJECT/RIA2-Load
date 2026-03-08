package com.load.service;

import com.load.dto.Rows.EventRow;
import com.load.dto.TestPayload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TestPayloadReader {

    private final JsonMapper jsonMapper;

    public TestPayloadReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public TestPayload read(byte[] bytes) {
        return jsonMapper.readValue(bytes, TestPayload.class);
    }

    public EventRow asEvent(TestPayload payload) {
        return new EventRow(
                payload.uid(),
                payload.dtstamp(),
                payload.dtstart(),
                payload.dtend(),
                payload.summary(),
                payload.description(),
                payload.categories(),
                payload.organizer(),
                payload.attendee(),
                payload.location(),
                payload.timezone()
        );
    }
}