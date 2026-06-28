package com.stream_forge.streamforge.services.encoding.model;


import java.nio.file.Path;

public record VideoJobContext(
        String movieId,
        Path jobDir,
        Path inputFile,
        Path encodedDir
) {}