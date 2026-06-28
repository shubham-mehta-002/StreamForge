"use client";

import React from 'react';
import { VideoPlayer } from './VideoPlayer';
import { Loader2, FileVideo } from 'lucide-react';
import { useVideos } from '@/hooks/useVideos';
import { Card, CardContent, CardHeader } from '@/components/ui/card';

export function VideoGallery() {
  const { videos, isLoading, error } = useVideos();

  if (isLoading) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-20 text-destructive">
        <p>{error}</p>
      </div>
    );
  }

  if (videos.length === 0) {
    return (
      <div className="text-center py-20 text-muted-foreground">
        <p>No videos available yet. Upload one to get started!</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 md:gap-8">
      {videos.map((video) => (
        <Card key={video.videoId} className="overflow-hidden group hover:shadow-xl transition-all duration-300 border-border/50">
          <div className="w-full bg-black">
             <VideoPlayer url={video.streamingUrl} />
          </div>
          <CardHeader className="p-4 pb-2">
            <div className="flex items-center gap-2">
              <FileVideo className="w-4 h-4 text-primary shrink-0" />
              <h3 className="font-semibold text-lg line-clamp-1" title={video.originalFileName}>
                {video.originalFileName}
              </h3>
            </div>
          </CardHeader>
          <CardContent className="p-4 pt-0">
            <p className="text-xs text-muted-foreground truncate">
              ID: {video.videoId}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
