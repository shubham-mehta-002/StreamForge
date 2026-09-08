import axios from 'axios';
import { api } from '@/lib/axios';
import type { Video, VideoStatusResponse, VideoStreamResponse } from '@/types/video';
import type { MultipartInitRequest, MultipartInitResponse, PartDetail } from '@/types/multipart';

export async function initiateMultipartUpload(payload: MultipartInitRequest): Promise<MultipartInitResponse> {
  const { data } = await api.post<MultipartInitResponse>('/multipart/initiate', payload);
  return data;
}

// Uses plain axios (not the shared `api` instance) because the request goes to S3,
// and adding backend headers would break the presigned URL signature.
export async function uploadPartToS3(
  presignedUrl: string,
  chunk: Blob,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<string> {
  const { headers } = await axios.put(presignedUrl, chunk, {
    signal,
    onUploadProgress({ loaded, total }) {
      if (total) onProgress?.(Math.round((loaded / total) * 50));
    },
  });

  onProgress?.(100);

  const etag: string | undefined = headers['etag'];
  if (!etag) throw new Error('S3 did not return an ETag for chunk');
  return etag;
}

export async function completeMultipartUpload(videoId: string, uploadId: string, parts: PartDetail[]): Promise<void> {
  await api.post('/multipart/complete', { videoId, uploadId, parts });
}

export async function abortMultipartUpload(videoId: string, uploadId: string): Promise<void> {
  try {
    await api.post('/multipart/abort', { videoId, uploadId });
  } catch (err) {
    console.warn('Abort request failed (non-fatal):', err);
  }
}

export async function getVideoStatus(videoId: string): Promise<VideoStatusResponse> {
  const { data } = await api.get<VideoStatusResponse>(`/${videoId}/status`);
  return data;
}

export async function getStreamingUrl(videoId: string): Promise<VideoStreamResponse> {
  const { data } = await api.get<VideoStreamResponse>(`/${videoId}/stream`);
  return data;
}

export async function getVideos(): Promise<Video[]> {
  const { data } = await api.get<Video[]>('');
  return data ?? [];
}
