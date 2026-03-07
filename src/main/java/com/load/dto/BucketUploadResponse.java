package com.load.dto;

public record BucketUploadResponse(
        String remote,
        String shareUrl,
        long expirationTime
) {
}