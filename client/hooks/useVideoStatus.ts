import { useState, useEffect, useCallback, useRef } from 'react';
import { toast } from 'sonner';
import { getVideoStatus, getStreamingUrl } from '@/services/api';

/** How often to poll while the video is still processing (milliseconds) */
const POLL_INTERVAL_MS = 5000;

/**
 * useVideoStatus — polls the video processing status every 3 seconds.
 *
 * Polling stops automatically once a terminal state is reached
 * (READY or FAILED) so we don't hammer the API indefinitely.
 *
 * On READY: fetches the HLS streaming URL and fires a success toast.
 * On FAILED: fires an error toast and stops polling.
 * Manual refresh is still available via the returned fetchStatus function.
 */
export function useVideoStatus(videoId: string) {
  const [status, setStatus] = useState<string | null>(null);
  const [streamUrl, setStreamUrl] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Prevents duplicate "ready" handling if multiple polls fire close together
  const processedReady = useRef(false);

  // Holds the interval ID so we can clear it when polling should stop
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  /** Clears the polling interval if one is running */
  const stopPolling = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  /**
   * Called once when status transitions to READY.
   * Fetches the HLS URL and fires a success toast — runs only once
   * thanks to the processedReady guard.
   */
  const handleVideoReady = useCallback(async () => {
    if (processedReady.current) return;
    processedReady.current = true;

    try {
      const streamData = await getStreamingUrl(videoId);
      setStreamUrl(streamData.streamingUrl);
      toast.success('Your video is processed and ready to stream!');
    } catch (err: any) {
      setError('Video is ready but failed to fetch streaming URL.');
      toast.error('Failed to load streaming URL.');
    }
  }, [videoId]);

  /**
   * Fetches the current status from the API.
   * Used both by the auto-poll interval and the manual refresh button.
   */
  const fetchStatus = useCallback(async () => {
    setIsRefreshing(true);
    setError(null);

    try {
      const data = await getVideoStatus(videoId);
      setStatus(data.status);

      if (data.status === 'READY') {
        // Terminal state — stop polling and load the stream URL
        stopPolling();
        await handleVideoReady();
      } else if (data.status === 'FAILED') {
        // Terminal state — stop polling and notify
        stopPolling();
        toast.error('Video processing failed.');
      }
      // Any other status (REQUESTED, PROCESSING) — polling continues
    } catch (err: any) {
      setError('Failed to fetch video status');
      // Don't stop polling on a transient network error —
      // the next tick will retry automatically
    } finally {
      setIsRefreshing(false);
    }
  }, [videoId, stopPolling, handleVideoReady]);

  useEffect(() => {
    // Fetch immediately on mount so the user sees a status right away
    fetchStatus();

    // Then poll every POLL_INTERVAL_MS until a terminal state is reached
    intervalRef.current = setInterval(() => {
      fetchStatus();
    }, POLL_INTERVAL_MS);

    // Cleanup: clear the interval when the component unmounts
    return () => {
      stopPolling();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoId]); // Re-run only if videoId changes (new upload)

  return {
    status,
    streamUrl,
    isRefreshing,
    error,
    fetchStatus, // still exposed for the manual refresh button
  };
}
