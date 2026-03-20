package com.load.dto;

public record LoadImportResult(
        String remote,
        int sizeBytes,
        String uid,
        String dtstart,
        String dtend,
        String bucketRemote,
        String shareUrl,
        long expirationTime
) {}
