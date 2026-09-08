export type VideoStatus = 'REQUESTED' | 'PROCESSING' | 'READY' | 'FAILED';

export interface Video {
  videoId: string;
  originalFileName: string;
  streamingUrl: string;
}

export interface VideoStatusResponse {
  id: string;
  status: VideoStatus;
}

export interface VideoStreamResponse {
  videoId: string;
  streamingUrl: string;
}
