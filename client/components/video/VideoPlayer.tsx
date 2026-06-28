"use client";

import React, { useEffect, useRef, useState } from 'react';
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

    let hls: Hls | null = null;

    if (Hls.isSupported()) {
      hls = new Hls({
        debug: false,
        enableWorker: true,
        // Handling CORS and standard S3 behaviors better
      });

      hls.loadSource(url);
      hls.attachMedia(video);

      hls.on(Hls.Events.ERROR, (event, data) => {
        console.error("HLS Error:", data);
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              setError("Network error: The video failed to load. This might be a CORS issue on the S3 bucket.");
              hls?.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              setError("Media error: The video stream is corrupted or unplayable.");
              hls?.recoverMediaError();
              break;
            default:
              setError("An unknown error occurred while playing the video.");
              hls?.destroy();
              break;
          }
        }
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Native HLS support (Safari)
      video.src = url;
      video.addEventListener('error', () => {
        setError("Error loading video natively.");
      });
    } else {
      setError("HLS is not supported in this browser.");
    }

    return () => {
      if (hls) {
        hls.destroy();
      }
    };
  }, [url]);

  return (
    <div className="w-full overflow-hidden rounded-xl shadow-lg border border-border/50 bg-black group relative">
      <div className="aspect-video relative bg-black flex items-center justify-center">
        {error && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/80 text-white p-6 text-center space-y-2">
            <AlertCircle className="w-8 h-8 text-destructive" />
            <p className="font-medium">{error}</p>
            <p className="text-xs opacity-75">Check browser console for more details.</p>
          </div>
        )}
        
        <video
          ref={videoRef}
          className="w-full h-full outline-none"
          controls
          crossOrigin="anonymous"
          playsInline
        />
      </div>
      
      {title && (
        <div className="absolute top-0 left-0 right-0 p-4 bg-gradient-to-b from-black/80 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none z-20">
          <h3 className="text-white font-medium text-lg truncate">{title}</h3>
        </div>
      )}
    </div>
  );
}
