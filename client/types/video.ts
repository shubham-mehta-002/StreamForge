export interface VideoStatus {
  id: string;
  status: 'uploading' | 'processing' | 'REQUESTED' | 'PROCESSING' | 'READY' | 'failed' | 'FAILED';
}

export interface VideoStream {
  videoId: string;
  streamingUrl: string;
}

export interface Video {
  videoId: string;
  originalFileName: string;
  streamingUrl: string;
  /**
   * Public S3 URL of the JPEG thumbnail.
   * Optional — may be null/undefined if thumbnail generation failed during encoding.
   * The UI should show a fallback when this is absent.
   */
  thumbnailUrl?: string | null;
}
