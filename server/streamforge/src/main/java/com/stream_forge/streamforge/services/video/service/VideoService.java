package com.stream_forge.streamforge.services.video.service;

import com.stream_forge.streamforge.entity.Video;
import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.entity.VideoUpdateRequest;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartAbortRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartCompleteRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartInitRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.MultipartInitResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoStatusResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoSummaryResponse;

import java.util.List;

public interface VideoService {

    // ── Legacy single-part upload ──────────────────────────────────────────
    InitUploadResponse createVideo(InitUploadRequest request);

    // ── Multipart upload lifecycle ─────────────────────────────────────────

    /**
     * Initiates a multipart upload session with S3 and creates a REQUESTED
     * video record. Returns the uploadId and one presigned URL per part.
     */
    MultipartInitResponse initiateMultipartUpload(MultipartInitRequest request);

    /**
     * Assembles all uploaded parts into the final S3 object by calling
     * S3's CompleteMultipartUpload. The video record remains in REQUESTED
     * status until the Lambda webhook triggers encoding.
     */
    void completeMultipartUpload(MultipartCompleteRequest request);

    /**
     * Aborts the multipart session and cleans up orphaned S3 parts.
     * Also marks the video record as FAILED in the database.
     */
    void abortMultipartUpload(MultipartAbortRequest request);

    // ── Shared ────────────────────────────────────────────────────────────
    Video getVideoById(String videoId);
    void updateVideoStatus(String videoId, VideoStatus status);
    void updateVideoDetails(String videoId, VideoUpdateRequest request);
    VideoStatusResponse getVideoStatus(String videoId);
    List<VideoSummaryResponse> getAllVideos();
}

