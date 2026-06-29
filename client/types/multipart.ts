/**
 * Types for the S3 multipart upload flow.
 *
 * Flow overview:
 *  1. Client calls initiateMultipartUpload  → gets uploadId + presigned URLs per part
 *  2. Client PUTs each chunk directly to S3 → collects ETags
 *  3. Client calls completeMultipartUpload  → server assembles final object in S3
 *  4. On cancel/error: client calls abortMultipartUpload → cleans up orphaned parts
 */

/** Request body for POST /videos/multipart/initiate */
export interface MultipartInitRequest {
    fileName: string;
    contentType: string;
    /** Number of chunks the file has been split into (each chunk >= 5 MB except last) */
    partCount: number;
}

/** One presigned PUT URL for a single file chunk */
export interface PartPresignedUrl {
    /** 1-based part index (S3 requirement) */
    partNumber: number;
    /** Presigned S3 PUT URL valid for 60 minutes */
    url: string;
}

/** Response from POST /videos/multipart/initiate */
export interface MultipartInitResponse {
    videoId: string;
    /** S3 multipart upload session token — needed for complete and abort */
    uploadId: string;
    /** S3 object key where the assembled file will live */
    s3Key: string;
    /** One presigned URL per chunk, ordered by partNumber */
    presignedUrls: PartPresignedUrl[];
}

/**
 * Represents one uploaded part.
 * partNumber: the 1-based index of the chunk
 * etag:       the ETag returned by S3 in the response header of the PUT request
 */
export interface PartDetail {
    partNumber: number;
    etag: string;
}

/**
 * Tracks the upload state of a single part during a multipart upload.
 * Used internally by useVideoUpload to manage per-part progress.
 */
export interface PartUploadState {
    partNumber: number;
    /** Upload progress for this part [0-100] */
    progress: number;
    /** Whether this part has successfully uploaded */
    done: boolean;
    /** ETag value returned by S3 after successful upload */
    etag: string | null;
}
