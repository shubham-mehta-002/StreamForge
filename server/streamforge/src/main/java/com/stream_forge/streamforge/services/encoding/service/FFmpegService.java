package com.stream_forge.streamforge.services.encoding.service;

import com.stream_forge.streamforge.services.encoding.model.VideoMetadata;
import com.stream_forge.streamforge.services.encoding.model.VideoProfile;

import java.nio.file.Path;

public interface FFmpegService {
    public VideoMetadata probe(Path input);
    public void encodeHls(Path input, Path outputDir, VideoProfile profile) throws Exception;
}
