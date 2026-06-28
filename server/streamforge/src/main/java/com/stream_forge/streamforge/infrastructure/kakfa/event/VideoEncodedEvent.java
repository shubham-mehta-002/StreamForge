package com.stream_forge.streamforge.infrastructure.kakfa.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl;          // Master playlist URL for streaming
    private String masterPlaylistKey; // S3 key of master.m3u8
    private boolean success;
    private String errorMessage;    // If encoding failed
}
