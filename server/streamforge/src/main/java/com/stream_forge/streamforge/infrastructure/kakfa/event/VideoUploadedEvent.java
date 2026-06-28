package com.stream_forge.streamforge.infrastructure.kakfa.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadedEvent {
    private String videoId;
    private String s3Key;
}
