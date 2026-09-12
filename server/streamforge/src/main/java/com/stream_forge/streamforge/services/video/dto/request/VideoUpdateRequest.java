package com.stream_forge.streamforge.services.video.dto.request;

import com.stream_forge.streamforge.entity.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUpdateRequest {

    private VideoStatus status;
    private String originalFileName;
    private String hlsMasterUrl;
    private String s3OriginalKey;
    private String spriteUrl;
    private Long duration;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private LocalDateTime processedAt;
    private String failureReason;
}
