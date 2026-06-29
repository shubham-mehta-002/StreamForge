package com.stream_forge.streamforge.services.video.controller;

import com.stream_forge.streamforge.services.streaming.dto.StreamingResponse;
import com.stream_forge.streamforge.services.streaming.service.StreamingService;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartAbortRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartCompleteRequest;
import com.stream_forge.streamforge.services.video.dto.request.MultipartInitRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.MultipartInitResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoStatusResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoSummaryResponse;
import com.stream_forge.streamforge.services.video.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/videos")
@Slf4j
@RequiredArgsConstructor
public class VideoUploadController {

    private final VideoService videoService;
    private final StreamingService streamingService;

    // ─────────────────────────────────────────────────────────────────────
    // Legacy single-part upload (kept for small files / testing)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /videos/init-upload
     *
     * Legacy endpoint — generates a single presigned PUT URL for the entire file.
     * Suitable for small files only. For anything > a few MB, use the multipart
     * endpoints below for parallel chunk uploading.
     */
    @PostMapping("/init-upload")
    public ResponseEntity<InitUploadResponse> initUpload(
            @Valid @RequestBody InitUploadRequest request) {
        InitUploadResponse response = videoService.createVideo(request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart upload lifecycle endpoints
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /videos/multipart/initiate
     *
     * Step 1: Opens a multipart upload session with S3. Creates a video record
     * in the database and returns:
     * - videoId     : the UUID for this video
     * - uploadId    : the S3 session token needed for all subsequent calls
     * - presignedUrls: one presigned PUT URL per part (client uploads in parallel)
     *
     * The client splits the file into chunks (recommended: 10 MB each) and
     * uploads each chunk to its corresponding presigned URL directly to S3.
     */
    @PostMapping("/multipart/initiate")
    public ResponseEntity<MultipartInitResponse> initiateMultipart(
            @Valid @RequestBody MultipartInitRequest request) {
        log.info("Multipart initiate request: file={} parts={}", request.getFileName(), request.getPartCount());
        return ResponseEntity.ok(videoService.initiateMultipartUpload(request));
    }

    /**
     * POST /videos/multipart/complete
     *
     * Step 2: Called after all parts have been PUT to S3.
     * Instructs S3 to assemble the parts into the final object.
     *
     * The client must supply the ETag returned by S3 for each part PUT response.
     * ETags are how S3 verifies data integrity before assembling.
     *
     * After this returns 200, S3 will fire an ObjectCreated event → Lambda →
     * /internal/s3/uploaded → encoding pipeline kicks off.
     */
    @PostMapping("/multipart/complete")
    public ResponseEntity<Void> completeMultipart(
            @Valid @RequestBody MultipartCompleteRequest request) {
        log.info("Multipart complete request: videoId={} parts={}", request.getVideoId(), request.getParts().size());
        videoService.completeMultipartUpload(request);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /videos/multipart/abort
     *
     * Abort: Called when the user cancels mid-upload or a fatal error occurs.
     * Cleans up all partial data from S3 (prevents storage charges for orphaned
     * parts) and marks the video as FAILED in the database.
     */
    @PostMapping("/multipart/abort")
    public ResponseEntity<Void> abortMultipart(
            @Valid @RequestBody MultipartAbortRequest request) {
        log.warn("Multipart abort request: videoId={}", request.getVideoId());
        videoService.abortMultipartUpload(request);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Streaming & status
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /videos/{videoId}/stream
     *
     * Returns the HLS master playlist URL for the given video.
     * The URL is cached in Redis (TTL: 60s) to avoid redundant DB lookups
     * on the hot streaming path.
     */
    @GetMapping("/{videoId}/stream")
    public ResponseEntity<StreamingResponse> getHLSUrl(@PathVariable String videoId) {
        return ResponseEntity.ok(streamingService.getStreamingUrl(videoId));
    }

    /**
     * GET /videos/{videoId}/status
     *
     * Returns the current processing status of a video:
     * REQUESTED → PROCESSING → READY / FAILED
     */
    @GetMapping("/{videoId}/status")
    public ResponseEntity<VideoStatusResponse> getVideoStatus(@PathVariable String videoId) {
        return ResponseEntity.ok(videoService.getVideoStatus(videoId));
    }

    /**
     * GET /videos
     *
     * Returns a summary list of all videos that have reached READY status,
     * including their streaming URLs for the gallery page.
     */
    @GetMapping
    public ResponseEntity<List<VideoSummaryResponse>> getAllVideos() {
        return ResponseEntity.ok(videoService.getAllVideos());
    }
}
