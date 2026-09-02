package com.stream_forge.streamforge.services.encoding.service;

import com.stream_forge.streamforge.services.encoding.model.VideoMetadata;
import com.stream_forge.streamforge.services.encoding.model.VideoProfile;

import java.nio.file.Path;

public interface FFmpegService {

    /**
     * Probes a video file using ffprobe to extract metadata:
     * width, height, duration, file size, and original filename.
     */
    VideoMetadata probe(Path input);

    /**
     * Encodes the input video to HLS format for the given quality profile.
     * Produces a playlist.m3u8 and segment_NNN.ts files in outputDir.
     */
    void encodeHls(Path input, Path outputDir, VideoProfile profile) throws Exception;
}
