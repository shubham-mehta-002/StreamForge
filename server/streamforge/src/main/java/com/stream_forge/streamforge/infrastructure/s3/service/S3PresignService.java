package com.stream_forge.streamforge.infrastructure.s3.service;

import com.stream_forge.streamforge.services.video.dto.response.MultipartInitResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;

public interface S3PresignService {

    /**
     * Generates a presigned PUT URL for a single-part (legacy) upload.
     * Still used for small files or testing.
     *
     * @param s3Key           S3 object key
     * @param expiryInMinutes how long the URL is valid
     * @return presigned PUT URL string
     */
    String generatePresignedUrl(String s3Key, int expiryInMinutes);

    // ─────────────────────────────────────────────────────────────────────
    // Multipart Upload API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Step 1: Tells S3 to begin a multipart upload for the given key.
     * Returns an uploadId that must be passed to all subsequent part uploads
     * and to the complete/abort calls.
     *
     * @param s3Key       the destination S3 object key
     * @param contentType MIME type of the video file
     * @return uploadId string assigned by S3
     */
    String initiateMultipartUpload(String s3Key, String contentType);

    /**
     * Step 2: Generates one presigned PUT URL for each part number.
     * The client uses these URLs to PUT individual file chunks directly to S3.
     *
     * Part numbers are 1-based (1 to partCount inclusive).
     *
     * @param s3Key     the destination S3 object key
     * @param uploadId  the multipart upload ID from initiateMultipartUpload()
     * @param partCount total number of parts the file will be split into
     * @return list of { partNumber, presignedUrl } pairs
     */
    List<MultipartInitResponse.PartPresignedUrl> generatePresignedUrlsForParts(
            String s3Key, String uploadId, int partCount);

    /**
     * Step 3: Instructs S3 to assemble all uploaded parts into the final object.
     * Must be called with the same uploadId and an ordered list of part ETags
     * (as returned by S3 in the response header of each part PUT).
     *
     * @param s3Key     the destination S3 object key
     * @param uploadId  the multipart upload ID
     * @param parts     list of { partNumber, etag } for every uploaded part
     */
    void completeMultipartUpload(String s3Key, String uploadId, List<CompletedPart> parts);

    /**
     * Abort: Cancels an in-progress multipart upload and cleans up all
     * already-uploaded parts from S3. Must be called on user cancel or
     * upload failure to avoid storage charges for orphaned parts.
     *
     * @param s3Key    the destination S3 object key
     * @param uploadId the multipart upload ID to abort
     */
    void abortMultipartUpload(String s3Key, String uploadId);
}

