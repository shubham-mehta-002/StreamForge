import { useState, useCallback, useRef } from 'react';
import {
  initiateMultipartUpload,
  uploadPartToS3,
  completeMultipartUpload,
  abortMultipartUpload,
} from '@/services/api';
import type { PartDetail } from '@/types/multipart';

const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB — S3 minimum per part
const MAX_CONCURRENT = 6;           // matches browser's per-host connection limit

interface UploadSession {
  videoId: string;
  uploadId: string;
}

export function useVideoUpload() {
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // refs don't trigger re-renders — used for internal upload coordination
  const sessionRef = useRef<UploadSession | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  // prevents double-abort: cancelUpload() sets this so the catch block skips its own abort call
  const abortedByUserRef = useRef(false);

  const uploadVideo = useCallback(async (file: File) => {
    setIsUploading(true);
    setUploadProgress(0);
    setError(null);
    setVideoId(null);
    abortedByUserRef.current = false;

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    const partCount = Math.ceil(file.size / CHUNK_SIZE);
    // plain array (not state) so per-byte callbacks don't each trigger a render
    const partProgress = new Array<number>(partCount).fill(0);

    // byte-weighted so the last (smaller) chunk doesn't skew the percentage
    const recalcProgress = () => {
      const uploadedBytes = partProgress.reduce((sum, pct, i) => {
        const start = i * CHUNK_SIZE;
        const partSize = Math.min(CHUNK_SIZE, file.size - start);
        return sum + (pct / 100) * partSize;
      }, 0);
      setUploadProgress(Math.round((uploadedBytes / file.size) * 100));
    };

    const uploadChunk = async (partNumber: number, url: string): Promise<PartDetail> => {
      const start = (partNumber - 1) * CHUNK_SIZE;
      const chunk = file.slice(start, Math.min(start + CHUNK_SIZE, file.size));

      const etag = await uploadPartToS3(
        url,
        chunk,
        (percent) => { partProgress[partNumber - 1] = percent; recalcProgress(); },
        abortController.signal,
      );

      partProgress[partNumber - 1] = 100;
      recalcProgress();
      return { partNumber, etag };
    };

    let session: UploadSession | null = null;

    try {
      // step 1: open an S3 multipart session, get one presigned URL per chunk
      const initData = await initiateMultipartUpload({
        fileName: file.name,
        contentType: file.type,
        partCount,
      });

      session = { videoId: initData.videoId, uploadId: initData.uploadId };
      sessionRef.current = session;

      // step 2: upload all chunks directly to S3 (≤ MAX_CONCURRENT at a time)
      const tasks = initData.presignedUrls.map(
        ({ partNumber, url }) => () => uploadChunk(partNumber, url),
      );

      const completedParts = await runWithConcurrency(tasks, MAX_CONCURRENT, abortController.signal);
      completedParts.sort((a, b) => a.partNumber - b.partNumber); // S3 requires ascending order

      // step 3: tell S3 to assemble all parts into the final object
      await completeMultipartUpload(session.videoId, session.uploadId, completedParts);

      setUploadProgress(100);
      setVideoId(session.videoId); // set last — signals UI to switch to status view

    } catch (err: unknown) {
      const isCancelled =
        abortController.signal.aborted ||
        (err instanceof Error && (
          err.message === 'Upload cancelled' ||
          (err as { code?: string }).code === 'ERR_CANCELED'
        ));

      if (!isCancelled) {
        setError(err instanceof Error ? err.message : 'Upload failed. Please try again.');
        console.error('[useVideoUpload]', err);
        // abort the S3 session to avoid orphaned parts incurring storage cost
        if (session && !abortedByUserRef.current) {
          await abortMultipartUpload(session.videoId, session.uploadId);
        }
      }

    } finally {
      sessionRef.current = null;
      abortControllerRef.current = null;
      setIsUploading(false);
    }
  }, []);

  const cancelUpload = useCallback(async () => {
    abortedByUserRef.current = true;       // tell catch block not to abort again
    abortControllerRef.current?.abort();   // cancel all in-flight S3 PUTs

    // clean up orphaned S3 parts
    const session = sessionRef.current;
    if (session) {
      sessionRef.current = null;
      await abortMultipartUpload(session.videoId, session.uploadId);
    }

    setIsUploading(false);
    setError('Upload cancelled.');
  }, []);

  const resetUpload = useCallback(() => {
    setVideoId(null);
    setUploadProgress(0);
    setError(null);
    sessionRef.current = null;
    abortControllerRef.current = null;
    abortedByUserRef.current = false;
  }, []);

  return { isUploading, uploadProgress, videoId, error, uploadVideo, cancelUpload, resetUpload, setError };
}

// runs tasks with a sliding window — starts a new task only when one finishes,
// keeping exactly `limit` in-flight at any time (unlike Promise.all which launches all at once)
async function runWithConcurrency<T>(
  tasks: Array<() => Promise<T>>,
  limit: number,
  signal: AbortSignal,
): Promise<T[]> {
  const results: T[] = new Array(tasks.length);
  let nextIndex = 0;

  // each worker pulls the next available task until the queue is empty
  const worker = async () => {
    while (nextIndex < tasks.length) {
      if (signal.aborted) throw new Error('Upload cancelled');
      const index = nextIndex++;
      results[index] = await tasks[index]();
    }
  };

  await Promise.all(Array.from({ length: Math.min(limit, tasks.length) }, worker));
  return results;
}
