import { useState, useEffect } from 'react';
import { getVideos } from '@/services/api';
import type { Video } from '@/types/video';

/**
 * Fetches all READY videos once on mount.
 * Returns loading / error state alongside the video list.
 */
export function useVideos() {
  const [videos, setVideos] = useState<Video[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const data = await getVideos();
        if (!cancelled) setVideos(data);
      } catch {
        if (!cancelled) setError('Failed to load videos. Please refresh the page.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, []);

  return { videos, isLoading, error };
}
