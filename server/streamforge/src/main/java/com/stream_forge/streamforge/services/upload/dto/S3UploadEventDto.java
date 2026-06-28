package com.stream_forge.streamforge.services.upload.dto;

import lombok.Data;

@Data
public class S3UploadEventDto {
    private String bucket;
    private String key;
}