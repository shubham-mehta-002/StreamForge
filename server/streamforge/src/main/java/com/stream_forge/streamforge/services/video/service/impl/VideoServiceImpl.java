package com.stream_forge.streamforge.services.video.service.impl;

import com.stream_forge.streamforge.services.video.dto.request.VideoUpdateRequest;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartAbortRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartCompleteRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartInitRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.MultipartInitResponse;
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
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;
    private final S3PresignService s3Service;

    // ─────────────────────────────────────────────────────────────────────
    // Legacy single-part upload (kept for backward compatibility)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public InitUploadResponse createVideo(InitUploadRequest request) {
        log.info("New single-part upload request received for: {}", request.getFileName());

        String videoId = UUID.randomUUID().toString();
        String s3Key = "videos/" + videoId + "/" + request.getFileName();

        // Generate a presigned PUT URL valid for 10 minutes
        String uploadUrl = s3Service.generatePresignedUrl(s3Key, 10);

        log.info("S3 key: {} | Presigned URL generated for: {}", s3Key, request.getFileName());

        Video video = new Video();
        video.setId(videoId);
        video.setS3OriginalKey(s3Key);
        video.setStatus(VideoStatus.REQUESTED);
        video.setOriginalFileName(request.getFileName());

        videoRepository.save(video);
        log.info("Video record saved with id: {}", videoId);

        return new InitUploadResponse(videoId, uploadUrl, s3Key, 600);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Step 1: Initiate
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a video record in REQUESTED state and kicks off the S3 multipart
     * upload session. Returns the uploadId and pre-signed URLs for every part
     * so the client can upload all chunks directly and in parallel.
     */
    @Override
    public MultipartInitResponse initiateMultipartUpload(MultipartInitRequest request) {
        log.info("Initiating multipart upload for: {} ({} parts)", request.getFileName(), request.getPartCount());

        String videoId = UUID.randomUUID().toString();
        String s3Key = "videos/" + videoId + "/" + request.getFileName();

        // Tell S3 to open a multipart session — returns a unique uploadId
        String uploadId = s3Service.initiateMultipartUpload(s3Key, request.getContentType());

        // Generate one presigned PUT URL per part (URLs are valid for 60 min each)
        List<MultipartInitResponse.PartPresignedUrl> partUrls =
                s3Service.generatePresignedUrlsForParts(s3Key, uploadId, request.getPartCount());

        // Persist the video record immediately so status polling works from this point on
        Video video = new Video();
        video.setId(videoId);
        video.setS3OriginalKey(s3Key);
        video.setStatus(VideoStatus.REQUESTED);
        video.setOriginalFileName(request.getFileName());

        videoRepository.save(video);
        log.info("Multipart session ready. videoId={} uploadId={}", videoId, uploadId);

        return new MultipartInitResponse(videoId, uploadId, s3Key, partUrls);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Step 2: Complete
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Called after all parts have been PUT to S3.
     * Instructs S3 to assemble the parts into the final object.
     * The video record stays REQUESTED until the Lambda webhook fires
     * (S3 ObjectCreated event) and triggers encoding.
     */
    @Override
    public void completeMultipartUpload(MultipartCompleteRequest request) {
        log.info("Completing multipart upload. videoId={} uploadId={} parts={}",
                request.getVideoId(), request.getUploadId(), request.getParts().size());

        // Fetch the s3Key from the video record to pass to the S3 API
        Video video = getVideoById(request.getVideoId());

        // Map the client-supplied { partNumber, etag } list to the AWS SDK type
        List<CompletedPart> completedParts = request.getParts().stream()
                .map(part -> CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getEtag())
                        .build())
                .collect(Collectors.toList());

        s3Service.completeMultipartUpload(video.getS3OriginalKey(), request.getUploadId(), completedParts);

        log.info("Multipart upload completed for videoId={}", request.getVideoId());
        // Note: status remains REQUESTED — the Lambda webhook moves it to PROCESSING
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Abort (on cancel or unrecoverable error)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Aborts the S3 multipart session (cleans up orphaned parts) and
     * marks the video record as FAILED so the UI can reflect the cancellation.
     */
    @Override
    public void abortMultipartUpload(MultipartAbortRequest request) {
        log.warn("Aborting multipart upload. videoId={} uploadId={}", request.getVideoId(), request.getUploadId());

        Video video = getVideoById(request.getVideoId());

        // Clean up partially uploaded parts from S3 to avoid storage charges
        s3Service.abortMultipartUpload(video.getS3OriginalKey(), request.getUploadId());

        // Mark the video as FAILED in the DB
        video.setStatus(VideoStatus.FAILED);
        videoRepository.save(video);

        log.info("Multipart upload aborted and video marked FAILED. videoId={}", request.getVideoId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Video getVideoById(String videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("Video not found with id: " + videoId));
    }

    @Override
    public void updateVideoStatus(String videoId, VideoStatus status) {
        Video video = getVideoById(videoId);
        video.setStatus(status);
        videoRepository.save(video);
    }

    @Override
    public void updateVideoDetails(String videoId, VideoUpdateRequest request) {
        Video video = getVideoById(videoId);

        if (request.getStatus() != null)           video.setStatus(request.getStatus());
        if (request.getOriginalFileName() != null) video.setOriginalFileName(request.getOriginalFileName());
        if (request.getHlsMasterUrl() != null)     video.setHlsMasterUrl(request.getHlsMasterUrl());
        if (request.getS3OriginalKey() != null)    video.setS3OriginalKey(request.getS3OriginalKey());
        if (request.getSpriteUrl() != null)        video.setSpriteUrl(request.getSpriteUrl());
        if (request.getDuration() != null)         video.setDuration(request.getDuration());
        if (request.getWidth() != null)            video.setWidth(request.getWidth());
        if (request.getHeight() != null)           video.setHeight(request.getHeight());
        if (request.getFileSize() != null)         video.setFileSize(request.getFileSize());
        if (request.getProcessedAt() != null)      video.setProcessedAt(request.getProcessedAt());
        if (request.getFailureReason() != null)    video.setFailureReason(request.getFailureReason());

        videoRepository.save(video);
    }

    @Override
    public VideoStatusResponse getVideoStatus(String videoId) {
        Video video = getVideoById(videoId);
        return new VideoStatusResponse(video.getId(), video.getStatus());
    }

    @Override
    public List<VideoSummaryResponse> getAllVideos() {
        return videoRepository.findByStatus(VideoStatus.READY)
                .stream()
                .map(video -> new VideoSummaryResponse(
                        video.getId(),
                        video.getOriginalFileName(),
                        video.getHlsMasterUrl()
                ))
                .toList();
    }
}
