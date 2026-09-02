package com.stream_forge.streamforge.services.encoding.util;


import com.stream_forge.streamforge.services.encoding.model.VideoJobContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.UUID;

// Not a @Service — registered manually as a @Bean in EncodingConfig so that
// the encoding.temp-dir property can be passed in via constructor (keeps the field final).
@Slf4j
public class JobContextFactory {

    private final String tempDir;

    public JobContextFactory(String tempDir) {
        this.tempDir = tempDir;
    }

    public VideoJobContext create(String videoId){
        // Note: Use UUID to ensures that even if the same movie is processed multiple times, each encoding job gets its own directory.
        String jobId = UUID.randomUUID().toString();

        // Note: Use Path instead of '+' concatenation to avoid \ or / issue (OS will identify what to use to create path)
        Path jobDir = Path.of(
                tempDir,
                videoId,
                jobId
        );

        // Note: resolve() -> appends the provided string to the base path
        Path inputFile = jobDir.resolve("raw_video"); //     /tmp/video-jobs/movie123/a7f4c5b1-1234-5678-9999-abcdef123456/raw_video.mp4
        Path encodedDir = jobDir.resolve("encoded");      //     /tmp/video-jobs/movie123/a7f4c5b1-1234-5678-9999-abcdef123456/encoded

        log.info("Inside context factory");

        return new VideoJobContext(
                videoId,
                jobDir, // base directory
                inputFile, // file path where raw video is stored
                encodedDir  // Directory path where encoded video will be stored
        );
    }
}