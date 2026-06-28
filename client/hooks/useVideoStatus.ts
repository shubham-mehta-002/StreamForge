import { useState, useEffect, useCallback, useRef } from 'react';
import { toast } from 'sonner';
import { getVideoStatus, getStreamingUrl } from '@/services/api';

export function useVideoStatus(videoId: string) {
  const [status, setStatus] = useState<string | null>(null);
  const [streamUrl, setStreamUrl] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const processedReady = useRef(false);

  const handleVideoReady = useCallback(async () => {
    if (processedReady.current) return;
    try {
      const streamData = await getStreamingUrl(videoId);
      setStreamUrl(streamData.streamingUrl);
      
      toast.success("Your video is processed and ready to stream!");
      processedReady.current = true;
    } catch (err: any) {
      setError("Video is ready but failed to fetch streaming URL.");
      toast.error("Failed to load streaming URL.");
    }
  }, [videoId]);

  const fetchStatus = useCallback(async () => {
    setIsRefreshing(true);
    setError(null);
    try {
      const data = await getVideoStatus(videoId);
      setStatus(data.status);
      
      if (data.status === 'READY' && !streamUrl) {
        await handleVideoReady();
      }
    } catch (err: any) {
      setError("Failed to fetch video status");
      toast.error("Failed to fetch video status");
    } finally {
      setIsRefreshing(false);
    }
  }, [videoId, streamUrl, handleVideoReady]);

  // Initial fetch
  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  return {
    status,
    streamUrl,
    isRefreshing,
    error,
    fetchStatus
  };
}
