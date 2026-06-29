import axios from 'axios';
import { Video, VideoStatus, VideoStream } from '@/types/video';
import {
  MultipartInitRequest,
  MultipartInitResponse,
  PartDetail,
} from '@/types/multipart';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';

/** Shared headers added to every backend request. */
const backendHeaders = {
  'ngrok-skip-browser-warning': 'true',
};

// ─────────────────────────────────────────────────────────────────────────────
// Legacy single-part upload (kept for reference / small file testing)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generates a single presigned S3 PUT URL for the whole file.
 * Only suitable for small files. For production use, prefer the multipart flow.
 */
export const getPresignedUrl = async (fileName: string, contentType: string) => {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/init-upload`,
      { fileName, contentType },
      { headers: backendHeaders }
    );
    const { videoId, uploadUrl, s3Key } = response.data;
    return { uploadUrl, videoId, s3Key };
  } catch (error) {
    console.error('Error fetching presigned URL:', error);
    throw new Error('Failed to initialize upload');
  }
};

/**
 * Uploads an entire file to S3 in a single PUT request.
 * Reports progress via the onProgress callback.
 */
export const uploadToS3 = async (
  uploadUrl: string,
  file: File,
  onProgress?: (progress: number, loaded: number, total: number) => void
) => {
  try {
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress?.(progress, progressEvent.loaded, progressEvent.total);
        }
      },
    });
  } catch (error) {
    console.error('Error uploading to S3:', error);
    throw new Error('Failed to upload video to S3');
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// Multipart Upload API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Step 1 — Initiate multipart upload.
 *
 * Tells the server to open an S3 multipart session. The server creates a
 * video record and returns:
 * - videoId       : UUID for this video
 * - uploadId      : S3 session token (needed for complete/abort)
 * - s3Key         : where the final object will live in S3
 * - presignedUrls : array of { partNumber, url } — one per chunk
 */
export const initiateMultipartUpload = async (
  payload: MultipartInitRequest
): Promise<MultipartInitResponse> => {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/multipart/initiate`,
      payload,
      { headers: backendHeaders }
    );
    return response.data as MultipartInitResponse;
  } catch (error) {
    console.error('Error initiating multipart upload:', error);
    throw new Error('Failed to initiate multipart upload');
  }
};

/**
 * Uploads a single chunk (part) directly to S3 using a presigned URL.
 *
 * @param presignedUrl  - the URL returned by initiateMultipartUpload for this part
 * @param chunk         - the Blob/slice of the original file for this part
 * @param onProgress    - optional callback reporting [0-100] progress for this part
 * @returns             the ETag from the S3 response header (needed for complete)
 */
export const uploadPartToS3 = async (
  presignedUrl: string,
  chunk: Blob,
  onProgress?: (progress: number) => void
): Promise<string> => {
  try {
    const response = await axios.put(presignedUrl, chunk, {
      // Do NOT set Content-Type here — the presigned URL already encodes
      // the expected content type; setting it again can cause a SignatureDoesNotMatch error.
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress?.(progress);
        }
      },
    });

    // S3 returns the ETag in the response headers — we need it for the complete call.
    // Axios lowercases all header names.
    const etag = response.headers['etag'];
    if (!etag) throw new Error('S3 did not return an ETag for the uploaded part');

    return etag;
  } catch (error) {
    console.error('Error uploading part to S3:', error);
    throw new Error('Failed to upload video part to S3');
  }
};

/**
 * Step 2 — Complete multipart upload.
 *
 * Sends all collected { partNumber, etag } pairs to the server.
 * The server calls S3's CompleteMultipartUpload, which assembles all
 * parts into the final object. After this, S3 fires an ObjectCreated
 * event → Lambda → encoding pipeline.
 */
export const completeMultipartUpload = async (
  videoId: string,
  uploadId: string,
  parts: PartDetail[]
): Promise<void> => {
  try {
    await axios.post(
      `${API_BASE_URL}/multipart/complete`,
      { videoId, uploadId, parts },
      { headers: backendHeaders }
    );
  } catch (error) {
    console.error('Error completing multipart upload:', error);
    throw new Error('Failed to complete multipart upload');
  }
};

/**
 * Abort — cancels the multipart session and cleans up S3 parts.
 *
 * Must be called when the user cancels or a fatal error occurs.
 * Without aborting, S3 keeps the partial parts and charges for storage.
 */
export const abortMultipartUpload = async (
  videoId: string,
  uploadId: string
): Promise<void> => {
  try {
    await axios.post(
      `${API_BASE_URL}/multipart/abort`,
      { videoId, uploadId },
      { headers: backendHeaders }
    );
  } catch (error) {
    // Log but don't rethrow — abort is best-effort cleanup
    console.error('Error aborting multipart upload:', error);
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// Status & Streaming
// ─────────────────────────────────────────────────────────────────────────────

/** Polls the current processing status of a video. */
export const getVideoStatus = async (videoId: string): Promise<VideoStatus> => {
  try {
    const response = await axios.get(`${API_BASE_URL}/${videoId}/status`, {
      headers: backendHeaders,
    });
    return {
      id: response.data.id || videoId,
      status: response.data.status,
    };
  } catch (error) {
    console.error('Error fetching video status:', error);
    throw new Error('Failed to fetch video status');
  }
};

/** Fetches the HLS master playlist URL for playback (served from Redis cache). */
export const getStreamingUrl = async (videoId: string): Promise<VideoStream> => {
  try {
    const response = await axios.get(`${API_BASE_URL}/${videoId}/stream`, {
      headers: backendHeaders,
    });
    return {
      videoId: response.data.videoId || videoId,
      streamingUrl: response.data.streamingUrl,
    };
  } catch (error) {
    console.error('Error fetching streaming URL:', error);
    throw new Error('Failed to fetch streaming URL');
  }
};

/** Fetches all READY videos for the gallery page. */
export const getVideos = async (): Promise<Video[]> => {
  try {
    const response = await axios.get(`${API_BASE_URL}`, { headers: backendHeaders });
    return response.data || [];
  } catch (error) {
    console.error('Error fetching videos:', error);
    throw new Error('Failed to fetch videos');
  }
};
