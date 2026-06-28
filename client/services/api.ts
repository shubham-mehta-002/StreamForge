import axios from 'axios';
import { Video, VideoStatus, VideoStream } from '@/types/video';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';

// 1. Get Pre-signed URL for uploading video to S3
export const getPresignedUrl = async (fileName: string, contentType: string) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/init-upload`, {
      fileName,
      contentType
    }, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
    const { videoId, uploadUrl, s3Key, expiresIn } = response.data;
    
    return {
      uploadUrl,
      videoId,
      s3Key,
    };
  } catch (error) {
    console.error("Error fetching presigned URL:", error);
    throw new Error("Failed to initialize upload");
  }
};

// 2. Upload video file to S3
export const uploadToS3 = async (uploadUrl: string, file: File, onProgress?: (progress: number, loaded: number, total: number) => void) => {
  try {
    await axios.put(uploadUrl, file, {
      headers: {
        'Content-Type': file.type,
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          if (onProgress) onProgress(progress, progressEvent.loaded, progressEvent.total);
        }
      },
    });
  } catch (error) {
    console.error("Error uploading to S3:", error);
    throw new Error("Failed to upload video to S3");
  }
};

// 3. Poll/Get status of video processing
export const getVideoStatus = async (videoId: string): Promise<VideoStatus> => {
  try {
    const response = await axios.get(`${API_BASE_URL}/${videoId}/status`, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
    return {
      id: response.data.id || videoId,
      status: response.data.status, 
    };
  } catch (error) {
    console.error("Error fetching video status:", error);
    throw new Error("Failed to fetch video status");
  }
};

// 4. Get Streaming URL when ready
export const getStreamingUrl = async (videoId: string): Promise<VideoStream> => {
  try {
    const response = await axios.get(`${API_BASE_URL}/${videoId}/stream`, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
    return {
      videoId: response.data.videoId || videoId,
      streamingUrl: response.data.streamingUrl,
    };
  } catch (error) {
    console.error("Error fetching streaming URL:", error);
    throw new Error("Failed to fetch streaming URL");
  }
};

// 5. Get list of all available videos for the gallery
export const getVideos = async (): Promise<Video[]> => {
  try {
    const response = await axios.get(`${API_BASE_URL}`, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
    return response.data || [];
  } catch (error) {
    console.error("Error fetching videos:", error);
    throw new Error("Failed to fetch videos");
  }
};
