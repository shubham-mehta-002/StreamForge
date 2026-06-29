"use client";

import React, { useCallback, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { UploadCloud, Video, FileVideo } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { UploadStatus } from './UploadStatus';
import { useVideoUpload } from '@/hooks/useVideoUpload';

export function VideoUpload() {
  const [file, setFile] = useState<File | null>(null);

  const {
    isUploading,
    uploadProgress,
    videoId,
    error,
    uploadVideo,
    resetUpload,
    cancelUpload,
    setError,
  } = useVideoUpload();

  const onDrop = useCallback((acceptedFiles: File[]) => {
    // Ignore new file drops while an upload is in progress
    if (isUploading) return;

    const videoFile = acceptedFiles.find(f => f.type.startsWith('video/'));
    if (videoFile) {
      setFile(videoFile);
      setError(null);
    } else {
      setError('Please select a valid video file.');
    }
  }, [isUploading, setError]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'video/*': ['.mp4', '.mov', '.avi', '.mkv', '.webm'] },
    maxFiles: 1,
    // Disable dropzone while uploading so user can't accidentally pick another file
    disabled: isUploading,
  });

  const handleUpload = () => {
    if (file) uploadVideo(file);
  };

  const handleReset = () => {
    setFile(null);
    resetUpload();
  };

  const handleCancel = async () => {
    await cancelUpload();
    setFile(null);
  };

  // ── Phase 3: upload done → show processing status tracker ─────────────
  // Only transition to UploadStatus AFTER the upload is fully complete
  // (isUploading = false AND videoId is set). Previously switching on videoId
  // alone caused the progress bar to disappear mid-upload.
  if (videoId && !isUploading) {
    return <UploadStatus videoId={videoId} onUploadAnother={handleReset} />;
  }

  // ── Phase 1 & 2: file selection + upload progress ─────────────────────
  return (
    <div className="space-y-6 w-full max-w-2xl mx-auto">
      <Card className="p-8 border-dashed border-2 hover:border-primary/50 transition-colors">

        {/* Dropzone — hidden while uploading so layout doesn't shift */}
        {!isUploading && (
          <div
            {...getRootProps()}
            className={`flex flex-col items-center justify-center gap-4 cursor-pointer min-h-[250px] ${isDragActive ? 'bg-primary/5' : ''}`}
          >
            <input {...getInputProps()} />

            {!file ? (
              // ── No file selected yet ──
              <>
                <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
                  <UploadCloud className="w-8 h-8 text-primary" />
                </div>
                <div className="text-center">
                  <h3 className="font-medium text-lg">Click to upload or drag and drop</h3>
                  <p className="text-muted-foreground text-sm mt-1">MP4, WebM, MOV up to 2GB</p>
                </div>
              </>
            ) : (
              // ── File selected, not yet uploading ──
              <div className="flex flex-col items-center gap-3">
                <FileVideo className="w-12 h-12 text-primary" />
                <p className="font-medium">{file.name}</p>
                <p className="text-sm text-muted-foreground">
                  {(file.size / (1024 * 1024)).toFixed(2)} MB
                </p>
                <div className="flex gap-2 mt-4" onClick={e => e.stopPropagation()}>
                  <Button variant="outline" onClick={handleReset}>Remove</Button>
                </div>
              </div>
            )}
          </div>
        )}

        {error && (
          <p className="text-destructive text-sm text-center mt-4">{error}</p>
        )}

        {/* ── Upload button — only show when file selected AND not uploading ── */}
        {file && !isUploading && (
          <div className="mt-6 flex justify-center">
            <Button onClick={handleUpload} className="w-full sm:w-auto" size="lg">
              <Video className="w-4 h-4 mr-2" /> Upload Video
            </Button>
          </div>
        )}

        {/* ── Progress bar — shown during upload, replaces the dropzone UI ── */}
        {isUploading && (
          <div className="min-h-[250px] flex flex-col items-center justify-center gap-6 px-4">
            <div className="flex flex-col items-center gap-2 text-center">
              <FileVideo className="w-10 h-10 text-primary" />
              <p className="font-medium text-base">{file?.name}</p>
              <p className="text-sm text-muted-foreground">
                Uploading directly to S3 in parallel chunks...
              </p>
            </div>

            <div className="w-full space-y-2">
              <div className="flex justify-between text-sm font-medium">
                <span>Uploading</span>
                <span>{uploadProgress}%</span>
              </div>
              <Progress value={uploadProgress} className="h-3 rounded-full" />
            </div>

            <Button variant="outline" size="sm" onClick={handleCancel}>
              Cancel Upload
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
}
