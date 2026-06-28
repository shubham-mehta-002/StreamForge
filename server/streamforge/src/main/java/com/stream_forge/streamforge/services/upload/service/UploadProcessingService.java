package com.stream_forge.streamforge.services.upload.service;

public interface UploadProcessingService {
    void processUploadedVideo(String bucketName,String objectKey);
}
