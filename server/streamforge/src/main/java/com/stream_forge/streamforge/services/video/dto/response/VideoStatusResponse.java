package com.stream_forge.streamforge.services.video.dto.response;

import com.stream_forge.streamforge.entity.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoStatusResponse {
    private String videoId;
    private VideoStatus status;
}
