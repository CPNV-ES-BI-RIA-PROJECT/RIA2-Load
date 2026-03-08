package com.load.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Rows {
    private Rows() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventRow(
            @JsonProperty("uid") String uid,
            @JsonProperty("dtstamp") String dtstamp,
            @JsonProperty("dtstart") String dtstart,
            @JsonProperty("dtend") String dtend,
            @JsonProperty("summary") String summary,
            @JsonProperty("description") String description,
            @JsonProperty("categories") String categories,
            @JsonProperty("organizer") String organizer,
            @JsonProperty("attendee") String attendee,
            @JsonProperty("location") String location,
            @JsonProperty("timezone") String timezone
    ) {}
}