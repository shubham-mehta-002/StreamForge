import { useState, useCallback } from 'react';
import { getPresignedUrl, uploadToS3 } from '@/services/api';

export function useVideoUpload() {
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [loadedBytes, setLoadedBytes] = useState(0);
  const [totalBytes, setTotalBytes] = useState(0);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const uploadVideo = async (file: File) => {
    setIsUploading(true);
    setUploadProgress(0);
    setError(null);
    
    try {
      // 1. Get Presigned URL
      const { uploadUrl, videoId: newVideoId } = await getPresignedUrl(file.name, file.type);
      setVideoId(newVideoId);
      
      // 2. Upload to S3
      await uploadToS3(uploadUrl, file, (progress, loaded, total) => {
        setUploadProgress(progress);
        setLoadedBytes(loaded);
        setTotalBytes(total);
      });
      
    } catch (err: any) {
      console.error(err);
      setError(err.message || "An error occurred during upload.");
    } finally {
      setIsUploading(false);
    }
  };

  const resetUpload = useCallback(() => {
    setVideoId(null);
    setUploadProgress(0);
    setLoadedBytes(0);
    setTotalBytes(0);
    setError(null);
  }, []);

  return {
    isUploading,
    uploadProgress,
    loadedBytes,
    totalBytes,
    videoId,
    error,
    uploadVideo,
    resetUpload,
    setError
  };
}
