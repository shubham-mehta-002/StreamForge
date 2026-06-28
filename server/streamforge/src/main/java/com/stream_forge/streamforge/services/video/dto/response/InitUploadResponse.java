package com.stream_forge.streamforge.services.video.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {
    private String videoId;
    private String uploadUrl;
    private String s3Key;
    private int expiresIn;
}