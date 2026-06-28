package com.stream_forge.streamforge.services.video.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSummaryResponse {
    private String videoId;
    private String originalFileName;
    private String streamingUrl;
}
