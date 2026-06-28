package com.stream_forge.streamforge.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record VideoUpdateRequest(
        VideoStatus status,
        String originalFileName,
        String hlsMasterUrl,
        String s3OriginalKey,
        String thumbnailUrl,
        String spriteUrl,
        Long duration,
        Integer width,
        Integer height,
        Long fileSize,
        LocalDateTime processedAt,
        String failureReason
) {}