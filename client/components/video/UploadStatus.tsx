'use client';

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
  const { status, streamUrl, isRefreshing, error, fetchStatus } = useVideoStatus(videoId);

  // Still loading the initial status
  if (!status) {
    return (
      <Card className="p-8 flex items-center justify-center min-h-[300px]">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </Card>
    );
  }

  const isReady = status === 'READY';
  const isFailed = status === 'FAILED';
  const isProcessing = !isReady && !isFailed;

  return (
    <Card className="p-6 md:p-8 space-y-6">

      {/* Header row */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {isReady ? (
            <CheckCircle2 className="w-8 h-8 text-green-500 shrink-0" />
          ) : isFailed ? (
            <AlertCircle className="w-8 h-8 text-destructive shrink-0" />
          ) : (
            <Loader2 className="w-8 h-8 text-blue-500 animate-spin shrink-0" />
          )}
          <div>
            <h3 className="font-semibold text-xl">
              {isReady ? 'Video Ready' : isFailed ? 'Processing Failed' : 'Processing…'}
            </h3>
            <p className="text-muted-foreground text-sm font-mono truncate max-w-[260px]">
              {videoId}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Badge
            variant={isReady ? 'default' : isFailed ? 'destructive' : 'secondary'}
            className="capitalize px-3 py-1 text-sm"
          >
            {status.toLowerCase()}
          </Badge>

          {/* Manual refresh — disabled once a terminal state is reached */}
          <Button
            variant="outline"
            size="icon"
            onClick={fetchStatus}
            disabled={isRefreshing || isReady || isFailed}
            title="Refresh status"
            aria-label="Refresh status"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {/* Transient error (e.g. network blip during polling) */}
      {error && (
        <div className="bg-destructive/10 text-destructive p-3 rounded-md text-sm">
          {error}
        </div>
      )}

      {/* Still encoding */}
      {isProcessing && (
        <div className="bg-secondary/30 rounded-lg p-6 text-center space-y-2">
          <p className="text-muted-foreground text-sm">
            Your video is being encoded for HLS streaming. This typically takes a few minutes.
          </p>
          <p className="text-xs text-muted-foreground">
            Status refreshes automatically every 5 seconds.
          </p>
        </div>
      )}

      {/* Preview when ready */}
      {isReady && streamUrl && (
        <div className="space-y-3">
          <h4 className="font-medium text-lg">Preview</h4>
          <VideoPlayer url={streamUrl} />
        </div>
      )}

      <div className="pt-2 flex justify-end">
        <Button onClick={onUploadAnother} variant={isReady ? 'default' : 'outline'}>
          Upload Another Video
        </Button>
      </div>
    </Card>
  );
}
