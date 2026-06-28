package com.stream_forge.streamforge.services.video.service.impl;

import com.stream_forge.streamforge.entity.VideoUpdateRequest;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoStatusResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoSummaryResponse;
import com.stream_forge.streamforge.entity.Video;
import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.exception.VideoNotFoundException;
import com.stream_forge.streamforge.services.video.repository.VideoRepository;
import com.stream_forge.streamforge.infrastructure.s3.service.S3PresignService;
import com.stream_forge.streamforge.services.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;

    private final S3PresignService s3Service;

    @Override
    public InitUploadResponse createVideo(InitUploadRequest request) {
        log.info("New request received for: {}", request.getFileName());

        String videoId = UUID.randomUUID().toString();

        String s3Key = "videos/" + videoId + "/" + request.getFileName();

        String uploadUrl = s3Service.generatePresignedUrl(
                s3Key,
                10
        );

        log.info("S3 key generated for {} : {}", request.getFileName(),s3Key);
        log.info("Presigned Url generated for {} : {}", request.getFileName(),uploadUrl);

        Video video = new Video();
        video.setId(videoId);
        video.setS3OriginalKey(s3Key);
        video.setStatus(VideoStatus.REQUESTED);
        video.setOriginalFileName(request.getFileName());


        Video saved = videoRepository.save(video);
        log.info("Saved object : {}", saved);

        return new InitUploadResponse(videoId,uploadUrl,s3Key,600);

    }

    @Override
    public Video getVideoById(String videoId){

        return videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("Video not found with id: " + videoId ));
    }

    @Override
    public void updateVideoStatus(String videoId, VideoStatus status){
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("Video not found with id: " + videoId ));

        video.setStatus(status);
        videoRepository.save(video);
    }

    @Override
    public void updateVideoDetails(String videoId, VideoUpdateRequest request) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("Video not found with id: " + videoId ));

        if (request.status() != null) {
            video.setStatus(request.status());
        }

        if (request.originalFileName() != null) {
            video.setOriginalFileName(request.originalFileName());
        }

        if (request.hlsMasterUrl() != null) {
            video.setHlsMasterUrl(request.hlsMasterUrl());
        }

        if (request.s3OriginalKey() != null) {
            video.setS3OriginalKey(request.s3OriginalKey());
        }

        if (request.thumbnailUrl() != null) {
            video.setThumbnailUrl(request.thumbnailUrl());
        }

        if (request.spriteUrl() != null) {
            video.setSpriteUrl(request.spriteUrl());
        }

        if (request.duration() != null) {
            video.setDuration(request.duration());
        }

        if (request.width() != null) {
            video.setWidth(request.width());
        }

        if (request.height() != null) {
            video.setHeight(request.height());
        }

        if (request.fileSize() != null) {
            video.setFileSize(request.fileSize());
        }

        if (request.processedAt() != null) {
            video.setProcessedAt(request.processedAt());
        }

        videoRepository.save(video);
    }

    @Override
    public VideoStatusResponse getVideoStatus(String videoId) {
        Video video = getVideoById(videoId);
        return new VideoStatusResponse(video.getId(), video.getStatus());
    }

    @Override
    public List<VideoSummaryResponse> getAllVideos() {
        return videoRepository.findAll()
                .stream()
                .filter((video) -> video.getStatus().equals(VideoStatus.READY))
                .map(video -> new VideoSummaryResponse(
                        video.getId(),
                        video.getOriginalFileName(),
                        video.getHlsMasterUrl()
                ))
                .toList();
    }

}
