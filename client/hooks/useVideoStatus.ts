import { useState, useEffect, useRef } from 'react';
import { toast } from 'sonner';
import { getVideoStatus, getStreamingUrl } from '@/services/api';
import type { VideoStatus } from '@/types/video';

const POLL_INTERVAL_MS = 5_000;

export function useVideoStatus(videoId: string) {
  const [status, setStatus] = useState<VideoStatus | null>(null);
  const [streamUrl, setStreamUrl] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // What it stores: the ID returned by setInterval, so you can cancel it later with clearInterval.
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // What it stores: a boolean flag — "have we already handled the READY state?"
  // Why it's needed:
  // The polling calls fetchStatus every 5 seconds.Without this guard, if the server returns READY on multiple consecutive polls before stopPolling fully takes effect, this block would run more than once:
  const readyHandledRef = useRef(false);

  const stopPolling = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  const fetchStatus = async () => {
    setIsRefreshing(true);
    try {
      const { status: nextStatus } = await getVideoStatus(videoId);
      setStatus(nextStatus);
      setError(null);

      if (nextStatus === 'READY') {
        stopPolling();
        if (!readyHandledRef.current) {
          readyHandledRef.current = true;
          const { streamingUrl } = await getStreamingUrl(videoId);
          setStreamUrl(streamingUrl);
          toast.success('Your video is ready to stream!');
        }
        return;
      }

      if (nextStatus === 'FAILED') {
        stopPolling();
        toast.error('Video processing failed. Please try uploading again.');
      }
    } catch {
      setError('Could not fetch video status. Retrying…');
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    intervalRef.current = setInterval(fetchStatus, POLL_INTERVAL_MS);
    return stopPolling;
  }, [videoId]);

  return { status, streamUrl, isRefreshing, error, fetchStatus };
}
