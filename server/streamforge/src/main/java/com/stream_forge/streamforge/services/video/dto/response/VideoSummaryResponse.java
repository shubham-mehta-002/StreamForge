package com.stream_forge.streamforge.services.video.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a READY video returned by GET /videos.
 * Contains everything the gallery page needs to render a video card
 * with a thumbnail poster and an inline HLS player.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSummaryResponse {
    private String videoId;
    private String originalFileName;
    private String streamingUrl;

    /**
     * Public S3 URL of the JPEG thumbnail image.
     * Null if thumbnail generation failed during encoding — the frontend
     * must handle this gracefully (show a fallback placeholder).
     */
    private String thumbnailUrl;
}
