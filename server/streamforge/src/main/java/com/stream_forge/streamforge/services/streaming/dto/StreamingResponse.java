package com.stream_forge.streamforge.services.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamingResponse {
    private String videoId;
    private String streamingUrl;   // presigned HLS master playlist url
}
