export interface VideoStatus {
  id: string;
  status: 'uploading' | 'processing' | 'READY' | 'failed' | 'FAILED';
}

export interface VideoStream {
  videoId: string;
  streamingUrl: string;
}

export interface Video {
  videoId: string;
  originalFileName: string;
  streamingUrl: string;
}
