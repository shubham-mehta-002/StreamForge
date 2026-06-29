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

    /**
     * Extracts a single JPEG thumbnail frame from the video.
     *
     * The frame is taken at the given timestamp (in seconds). The output image
     * is written to the provided outputPath. If the timestamp exceeds the video
     * duration, FFmpeg falls back to the nearest available frame.
     *
     * @param input      path to the local raw video file
     * @param outputPath path where the JPEG thumbnail should be written
     * @param atSeconds  timestamp in seconds to extract the frame from
     * @throws Exception if FFmpeg exits with a non-zero code
     */
    void generateThumbnail(Path input, Path outputPath, long atSeconds) throws Exception;
}
