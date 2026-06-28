package com.stream_forge.streamforge.services.video.dto.request;

import lombok.Data;

@Data
public class InitUploadRequest {
    private String fileName;
    private String contentType;
}