package com.stream_forge.streamforge.services.streaming.service;

import com.stream_forge.streamforge.services.streaming.dto.StreamingResponse;

public interface StreamingService {
    public StreamingResponse getStreamingUrl(String videoId);
}
