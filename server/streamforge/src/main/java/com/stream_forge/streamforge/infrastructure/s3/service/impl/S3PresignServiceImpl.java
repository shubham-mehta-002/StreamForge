package com.stream_forge.streamforge.infrastructure.s3.service.impl;

import com.stream_forge.streamforge.infrastructure.s3.service.S3PresignService;
import com.stream_forge.streamforge.services.video.dto.response.MultipartInitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3PresignServiceImpl implements S3PresignService {

    private final S3Presigner s3Presigner;

    // S3Client is needed for multipart lifecycle operations (initiate, complete, abort)
    // because those are regular AWS API calls, not presigned URL generation.
    private final S3Client s3Client;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.presigned-hls-playlist-url-expiry}")
    private long presignedHlsPlaylistUrlExpiry;

    // ─────────────────────────────────────────────────────────────────────
    // Legacy single-part presigned PUT URL (kept for small files / testing)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public String generatePresignedUrl(String s3Key, int expiryInMinutes) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryInMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        log.info("Generated single-part presigned URL for key: {}", s3Key);
        return presignedRequest.url().toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Step 1: Initiate
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers a new multipart upload with S3 and returns an uploadId.
     * This ID acts as a session token — every subsequent part upload and
     * the final complete/abort call must reference this ID.
     */
    @Override
    public String initiateMultipartUpload(String s3Key, String contentType) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);

        log.info("Multipart upload initiated. uploadId={} key={}", response.uploadId(), s3Key);
        return response.uploadId();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Step 2: Generate presigned URLs for each part
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates one presigned URL per part so the client can PUT chunks
     * directly to S3 without routing bytes through this server.
     *
     * Each URL is valid for 60 minutes — enough time for large file parts
     * even on slower connections. Part numbers are 1-based (S3 requirement).
     */
    @Override
    public List<MultipartInitResponse.PartPresignedUrl> generatePresignedUrlsForParts(
            String s3Key, String uploadId, int partCount) {

        List<MultipartInitResponse.PartPresignedUrl> presignedUrls = new ArrayList<>();

        for (int partNumber = 1; partNumber <= partCount; partNumber++) {

            // Build the underlying UploadPart request (identifies which part of which upload)
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            // Wrap it in a presign request with a 60-minute window
            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            String url = s3Presigner.presignUploadPart(presignRequest).url().toString();

            presignedUrls.add(new MultipartInitResponse.PartPresignedUrl(partNumber, url));

            log.debug("Presigned URL generated for part {}/{} of uploadId={}", partNumber, partCount, uploadId);
        }

        log.info("Generated {} presigned part URLs for uploadId={}", partCount, uploadId);
        return presignedUrls;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Step 3: Complete
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Signals S3 to assemble all uploaded parts into the final object.
     *
     * S3 validates that:
     * - All part ETags match what was uploaded
     * - Parts are listed in ascending partNumber order
     * - Every part (except the last) was >= 5 MB
     *
     * After this call succeeds, the S3 object is accessible and the
     * multipart upload session is closed.
     */
    @Override
    public void completeMultipartUpload(String s3Key, String uploadId, List<CompletedPart> parts) {
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                .parts(parts)
                .build();

        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .uploadId(uploadId)
                .multipartUpload(completedUpload)
                .build();

        s3Client.completeMultipartUpload(request);

        log.info("Multipart upload completed. key={} uploadId={} parts={}", s3Key, uploadId, parts.size());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload — Abort (cleanup on cancel / error)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Cancels the multipart upload and deletes all parts already stored in S3.
     *
     * This MUST be called whenever an upload is abandoned (user cancel,
     * unrecoverable error, etc.). Incomplete multipart uploads are invisible
     * as objects but S3 still charges for the stored part bytes until aborted.
     */
    @Override
    public void abortMultipartUpload(String s3Key, String uploadId) {
        AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .uploadId(uploadId)
                .build();

        s3Client.abortMultipartUpload(request);

        log.info("Multipart upload aborted. key={} uploadId={}", s3Key, uploadId);
    }
}
