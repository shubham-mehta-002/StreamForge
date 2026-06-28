package com.stream_forge.streamforge.services.encoding.model;

public record VideoMetadata(
        String originalFileName,
        Long fileSize,
        String format,
        Long duration,
        Integer width,
        Integer height
) {}