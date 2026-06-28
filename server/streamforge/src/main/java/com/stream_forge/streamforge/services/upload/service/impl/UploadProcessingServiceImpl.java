package com.stream_forge.streamforge.services.upload.service.impl;

import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.services.encoding.service.EncodingService;
import com.stream_forge.streamforge.services.upload.service.UploadProcessingService;
import com.stream_forge.streamforge.services.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadProcessingServiceImpl implements UploadProcessingService {
    private final VideoService videoService;
    private final EncodingService encodingService;


    @Override
    public void processUploadedVideo(String bucketName,String s3Key) {

        log.info("New upload detected. bucket={} key={}",bucketName,s3Key);

        String videoId = extractVideoId(s3Key);

        videoService.updateVideoStatus(videoId, VideoStatus.PROCESSING);
        encodingService.encodeVideo(videoId, s3Key);
    }

    private String extractVideoId(String key) {
        String[] parts = key.split("/");

        return parts[1];
    }
}
