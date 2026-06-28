package com.stream_forge.streamforge.infrastructure.s3.service;

public interface S3PresignService {
    public String generatePresignedUrl(String s3key, int expiryInMinutes);
}
