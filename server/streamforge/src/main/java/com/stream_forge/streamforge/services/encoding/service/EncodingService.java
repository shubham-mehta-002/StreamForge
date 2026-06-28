package com.stream_forge.streamforge.services.encoding.service;

import com.stream_forge.streamforge.infrastructure.kakfa.event.VideoUploadedEvent;

public interface EncodingService {
    public void encodeVideo(String videoId, String s3Key);
}
