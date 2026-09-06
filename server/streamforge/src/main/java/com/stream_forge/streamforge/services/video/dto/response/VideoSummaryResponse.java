package com.stream_forge.streamforge.services.video.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a READY video returned by GET /videos.
 * Contains everything the gallery page needs to render a video card
 * with an inline HLS player.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSummaryResponse {
    private String videoId;
    private String originalFileName;
    private String streamingUrl;
}
