'use client';

import { useState, useEffect } from 'react';
import { Loader2, FileVideo, PlayCircle, X } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { VideoPlayer } from './VideoPlayer';
import { useVideos } from '@/hooks/useVideos';
import type { Video } from '@/types/video';

function VideoModal({ video, onClose }: { video: Video; onClose: () => void }) {
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    const handleKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKey);
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('keydown', handleKey);
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="relative w-full max-w-4xl">
        <button
          onClick={onClose}
          className="absolute -top-10 right-0 text-white/80 hover:text-white transition-colors flex items-center gap-1.5 text-sm"
          aria-label="Close player"
        >
          <X className="w-5 h-5" /> Close
        </button>

        <VideoPlayer url={video.streamingUrl} title={video.originalFileName} />

        <div className="mt-3 flex items-center gap-2 text-white/70">
          <FileVideo className="w-4 h-4 shrink-0" />
          <span className="text-sm truncate">{video.originalFileName}</span>
        </div>
      </div>
    </div>
  );
}

function VideoCard({ video, onClick }: { video: Video; onClick: () => void }) {
  return (
    <Card
      className="overflow-hidden cursor-pointer group hover:shadow-xl hover:-translate-y-0.5 transition-all duration-200 border-border/50"
      onClick={onClick}
    >
      <div className="relative aspect-video bg-muted flex items-center justify-center overflow-hidden">
        <FileVideo className="absolute w-20 h-20 text-muted-foreground/10" />
        <PlayCircle className="w-14 h-14 text-white/60 group-hover:text-white group-hover:scale-110 transition-all duration-200 drop-shadow-lg z-10" />
      </div>

      <CardContent className="p-4">
        <p className="font-medium text-sm line-clamp-1 group-hover:text-primary transition-colors" title={video.originalFileName}>
          {video.originalFileName}
        </p>
        <p className="text-xs text-muted-foreground font-mono truncate mt-1">{video.videoId}</p>
      </CardContent>
    </Card>
  );
}

export function VideoGallery() {
  const { videos, isLoading, error } = useVideos();
  const [selected, setSelected] = useState<Video | null>(null);

  if (isLoading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>;
  if (error) return <div className="text-center py-20 text-destructive text-sm">{error}</div>;
  if (!videos.length) return <div className="text-center py-20 text-muted-foreground text-sm">No videos yet. Upload one to get started!</div>;

  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
        {videos.map((video) => (
          <VideoCard key={video.videoId} video={video} onClick={() => setSelected(video)} />
        ))}
      </div>
      {selected && <VideoModal video={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
