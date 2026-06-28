import { useState, useEffect } from 'react';
import { Video } from '@/types/video';
import { getVideos } from '@/services/api';

export function useVideos() {
  const [videos, setVideos] = useState<Video[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchVideos = async () => {
      try {
        const data = await getVideos();
        setVideos(data);
      } catch (err: any) {
        setError("Failed to load videos");
      } finally {
        setIsLoading(false);
      }
    };
    fetchVideos();
  }, []);

  return { videos, isLoading, error };
}
