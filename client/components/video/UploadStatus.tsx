"use client";

import React from 'react';
import { RefreshCw, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { VideoPlayer } from './VideoPlayer';
import { useVideoStatus } from '@/hooks/useVideoStatus';

interface UploadStatusProps {
  videoId: string;
  onUploadAnother: () => void;
}

export function UploadStatus({ videoId, onUploadAnother }: UploadStatusProps) {
  const { 
    status, 
    streamUrl, 
    isRefreshing, 
    error, 
    fetchStatus 
  } = useVideoStatus(videoId);

  if (!status) {
    return (
      <Card className="p-8 flex items-center justify-center min-h-[300px]">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </Card>
    );
  }
  
  const isReady = status === 'READY';
  const isFailed = status === 'failed' || status === 'FAILED';
  const isProcessing = !isReady && !isFailed;

  return (
    <Card className="p-6 md:p-8 space-y-6">
      <div className="flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {isReady ? (
            <CheckCircle2 className="w-8 h-8 text-green-500" />
          ) : isFailed ? (
            <AlertCircle className="w-8 h-8 text-destructive" />
          ) : (
            <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
          )}
          
          <div>
            <h3 className="font-semibold text-xl">
              {isReady ? 'Video Ready' : isFailed ? 'Processing Failed' : 'Processing Video'}
            </h3>
            <p className="text-muted-foreground text-sm">
              ID: {videoId}
            </p>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <Badge variant={isReady ? "default" : isFailed ? "destructive" : "secondary"} className="capitalize px-3 py-1 text-sm">
            {status}
          </Badge>
          
          <Button 
            variant="outline" 
            size="icon" 
            onClick={fetchStatus} 
            disabled={isRefreshing || isReady || isFailed}
            title="Refresh Status"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {error && (
        <div className="bg-destructive/10 text-destructive p-3 rounded-md text-sm">
          {error}
        </div>
      )}

      {isProcessing && (
        <div className="bg-secondary/30 p-6 rounded-lg text-center space-y-2">
          <p className="text-muted-foreground">Your video is currently being encoded for streaming. This may take a few minutes depending on the file size.</p>
          <p className="text-xs text-muted-foreground mt-2">You can click refresh to check manually or we will notify you when it's done.</p>
        </div>
      )}

      {isReady && streamUrl && (
        <div className="mt-6 space-y-4">
          <h4 className="font-medium text-lg">Preview</h4>
          <VideoPlayer url={streamUrl} />
        </div>
      )}

      <div className="pt-4 flex justify-end">
        <Button onClick={onUploadAnother} variant={isReady ? "default" : "outline"}>
          Upload Another Video
        </Button>
      </div>
    </Card>
  );
}
