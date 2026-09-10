"use client";

import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { AlertCircle } from 'lucide-react';

interface VideoPlayerProps {
  url: string;
  title?: string;
}

export function VideoPlayer({ url, title }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    setError(null);

    if (Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true, startLevel: -1 });
      let networkRetries = 0;

      hls.loadSource(url);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => { });
      });

      hls.on(Hls.Events.ERROR, (_, data) => {
        if (!data.fatal) return;

        if (data.type === Hls.ErrorTypes.NETWORK_ERROR && ++networkRetries <= 3) {
          hls.startLoad();
          return;
        }

        if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError();
        } else {
          hls.destroy();
        }

        setError(
          data.type === Hls.ErrorTypes.NETWORK_ERROR
            ? 'Network error — failed to load video. Check your connection.'
            : 'Error playing the video. The stream may be corrupted.'
        );
      });

      return () => hls.destroy();
    }

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url;
      video.play().catch(() => { });
      const onError = () => setError('Error loading video.');
      video.addEventListener('error', onError);
      return () => video.removeEventListener('error', onError);
    }

    setError('HLS playback is not supported in this browser.');
  }, [url]);

  return (
    <div className="w-full overflow-hidden rounded-xl shadow-lg border border-border/50 bg-black group relative">
      <div className="aspect-video relative">
        {error && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/80 text-white p-6 text-center space-y-2">
            <AlertCircle className="w-8 h-8 text-destructive" />
            <p className="font-medium">{error}</p>
          </div>
        )}

        <video
          ref={videoRef}
          className="w-full h-full outline-none"
          controls
          crossOrigin="anonymous"
          playsInline
          muted
        />

        {title && (
          <div className="absolute top-0 inset-x-0 p-4 bg-gradient-to-b from-black/80 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none z-20">
            <h3 className="text-white font-medium text-lg truncate">{title}</h3>
          </div>
        )}
      </div>
    </div>
  );
}
