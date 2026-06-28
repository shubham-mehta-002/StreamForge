package com.stream_forge.streamforge.services.encoding.model;

public record VideoProfile(
        String name,
        int width,
        int height,
        int bitrateKbps,    // How much data is used to represent 1 second of video
        int maxRateKbps,    // total size of segment
        int bufferSizeKbps  // allowed extra limit for bitrate
) {}