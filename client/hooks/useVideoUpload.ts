import { useState, useCallback, useRef } from 'react';
import {
  initiateMultipartUpload,
  uploadPartToS3,
  completeMultipartUpload,
  abortMultipartUpload,
} from '@/services/api';
import { PartDetail, PartUploadState } from '@/types/multipart';

/**
 * Chunk size for splitting the file into parts.
 *
 * 10 MB is a good balance:
 * - Above the 5 MB S3 minimum for non-last parts
 * - Small enough to keep per-part retries cheap
 * - Large enough to limit the number of presigned URLs / API calls
 */
const CHUNK_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

/**
 * Maximum number of parts uploading simultaneously.
 *
 * 4 concurrent uploads is a safe default that saturates a typical connection
 * without overloading the browser's HTTP connection pool.
 */
const MAX_CONCURRENT_UPLOADS = 4;

/**
 * useVideoUpload — manages the full multipart S3 upload lifecycle.
 *
 * Flow:
 *  1. Split file into CHUNK_SIZE_BYTES chunks
 *  2. POST /multipart/initiate → get uploadId + one presigned URL per part
 *  3. Upload all parts in parallel (up to MAX_CONCURRENT_UPLOADS at a time)
 *     - Track per-part progress and aggregate into a single [0-100] value
 *     - Collect ETags returned by S3 for each part
 *  4. POST /multipart/complete → server assembles the final S3 object
 *  5. On cancel / error: POST /multipart/abort → clean up orphaned S3 parts
 */
export function useVideoUpload() {
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [loadedBytes, setLoadedBytes] = useState(0);
  const [totalBytes, setTotalBytes] = useState(0);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  /**
   * Stores { videoId, uploadId } of the in-progress upload so we can
   * abort it if the user cancels before completion.
   */
  const activeUploadRef = useRef<{ videoId: string; uploadId: string } | null>(null);

  // ─────────────────────────────────────────────────────────────────────
  // Main upload function
  // ─────────────────────────────────────────────────────────────────────

  const uploadVideo = async (file: File) => {
    setIsUploading(true);
    setUploadProgress(0);
    setLoadedBytes(0);
    setTotalBytes(file.size);
    setError(null);

    // Calculate how many parts we need (ceiling division)
    const partCount = Math.ceil(file.size / CHUNK_SIZE_BYTES);

    let currentVideoId: string | null = null;
    let currentUploadId: string | null = null;

    try {
      // ── Step 1: Initiate ───────────────────────────────────────────────
      // Server opens an S3 multipart session and returns presigned URLs
      const initData = await initiateMultipartUpload({
        fileName: file.name,
        contentType: file.type,
        partCount,
      });

      currentVideoId = initData.videoId;
      currentUploadId = initData.uploadId;

      // Store so abort() can reference them if the user cancels
      activeUploadRef.current = { videoId: currentVideoId, uploadId: currentUploadId };
      setVideoId(currentVideoId);

      // ── Step 2: Upload parts in parallel ──────────────────────────────
      // Each part tracks its own progress; we aggregate for the overall bar.
      const partStates: PartUploadState[] = initData.presignedUrls.map((p) => ({
        partNumber: p.partNumber,
        progress: 0,
        done: false,
        etag: null,
      }));

      /**
       * Updates the overall progress bar by summing the progress of
       * all individual parts, weighted equally.
       */
      const updateOverallProgress = () => {
        const totalPartProgress = partStates.reduce((sum, p) => sum + p.progress, 0);
        const overallPercent = Math.round(totalPartProgress / partCount);
        setUploadProgress(overallPercent);

        // Approximate loaded bytes from overall percentage
        setLoadedBytes(Math.round((overallPercent / 100) * file.size));
      };

      /**
       * Uploads a single part and returns its ETag.
       *
       * @param partNumber - 1-based part index
       * @param url        - presigned PUT URL for this part
       * @returns PartDetail with partNumber and etag
       */
      const uploadPart = async (partNumber: number, url: string): Promise<PartDetail> => {
        // Slice the file into the chunk for this part
        const start = (partNumber - 1) * CHUNK_SIZE_BYTES;
        const end = Math.min(start + CHUNK_SIZE_BYTES, file.size);
        const chunk = file.slice(start, end);

        const etag = await uploadPartToS3(url, chunk, (partProgress) => {
          // Update this part's progress and recalculate the aggregate
          partStates[partNumber - 1].progress = partProgress;
          updateOverallProgress();
        });

        partStates[partNumber - 1].done = true;
        partStates[partNumber - 1].etag = etag;
        partStates[partNumber - 1].progress = 100;
        updateOverallProgress();

        return { partNumber, etag };
      };

      // Run all part uploads with a concurrency limit of MAX_CONCURRENT_UPLOADS
      const completedParts = await runWithConcurrency(
        initData.presignedUrls.map((p) => () => uploadPart(p.partNumber, p.url)),
        MAX_CONCURRENT_UPLOADS
      );

      // ── Step 3: Complete ───────────────────────────────────────────────
      // Tell S3 to assemble all parts. Parts must be sorted by partNumber.
      const sortedParts = completedParts.sort((a, b) => a.partNumber - b.partNumber);
      await completeMultipartUpload(currentVideoId, currentUploadId, sortedParts);

      setUploadProgress(100);
      setLoadedBytes(file.size);

      // Clear the active upload ref — upload is done, abort is no longer needed
      activeUploadRef.current = null;

    } catch (err: any) {
      console.error('Upload failed:', err);
      setError(err.message || 'An error occurred during upload.');

      // ── Abort on failure ───────────────────────────────────────────────
      // Clean up orphaned S3 parts to avoid storage charges
      if (currentVideoId && currentUploadId) {
        await abortMultipartUpload(currentVideoId, currentUploadId);
      }
      activeUploadRef.current = null;

    } finally {
      setIsUploading(false);
    }
  };

  // ─────────────────────────────────────────────────────────────────────
  // Cancel upload (called when user clicks "Cancel" mid-upload)
  // ─────────────────────────────────────────────────────────────────────

  const cancelUpload = useCallback(async () => {
    if (!activeUploadRef.current) return;

    const { videoId: vid, uploadId } = activeUploadRef.current;
    activeUploadRef.current = null;

    // Abort the S3 session to clean up partial parts
    await abortMultipartUpload(vid, uploadId);

    setIsUploading(false);
    setError('Upload cancelled.');
  }, []);

  // ─────────────────────────────────────────────────────────────────────
  // Reset state for "Upload Another" flow
  // ─────────────────────────────────────────────────────────────────────

  const resetUpload = useCallback(() => {
    setVideoId(null);
    setUploadProgress(0);
    setLoadedBytes(0);
    setTotalBytes(0);
    setError(null);
    activeUploadRef.current = null;
  }, []);

  return {
    isUploading,
    uploadProgress,
    loadedBytes,
    totalBytes,
    videoId,
    error,
    uploadVideo,
    cancelUpload,
    resetUpload,
    setError,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Concurrency utility
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Runs an array of async task factories with a maximum concurrency limit.
 *
 * Unlike Promise.all() (which runs everything at once), this keeps at most
 * `limit` tasks in-flight at any time. As each task finishes, the next
 * queued task starts immediately.
 *
 * @param tasks  - array of functions that return a Promise
 * @param limit  - maximum number of concurrent in-flight promises
 * @returns      - array of all resolved values, in the same order as tasks
 */
async function runWithConcurrency<T>(
  tasks: Array<() => Promise<T>>,
  limit: number
): Promise<T[]> {
  const results: T[] = new Array(tasks.length);
  let nextIndex = 0;

  /**
   * Worker: picks the next unstarted task, runs it, stores the result,
   * and then picks the next one — until all tasks are done.
   */
  const worker = async () => {
    while (nextIndex < tasks.length) {
      const index = nextIndex++;
      results[index] = await tasks[index]();
    }
  };

  // Spawn `limit` workers — they share the `nextIndex` cursor via closure
  const workers = Array.from({ length: Math.min(limit, tasks.length) }, () => worker());
  await Promise.all(workers);

  return results;
}
