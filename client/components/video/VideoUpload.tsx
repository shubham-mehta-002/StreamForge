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
    setError 
  } = useVideoUpload();

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const videoFile = acceptedFiles.find(f => f.type.startsWith('video/'));
    if (videoFile) {
      setFile(videoFile);
      setError(null);
    } else {
      setError("Please select a valid video file.");
    }
  }, [setError]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'video/*': ['.mp4', '.mov', '.avi', '.mkv', '.webm']
    },
    maxFiles: 1
  });

  const handleUpload = () => {
    if (file) {
      uploadVideo(file);
    }
  };

  const handleReset = () => {
    setFile(null);
    resetUpload();
  };

  return (
    <div className="space-y-6 w-full max-w-2xl mx-auto">
      {!videoId ? (
        <Card className="p-8 border-dashed border-2 hover:border-primary/50 transition-colors">
          <div 
            {...getRootProps()} 
            className={`flex flex-col items-center justify-center gap-4 cursor-pointer min-h-[250px] ${isDragActive ? 'bg-primary/5' : ''}`}
          >
            <input {...getInputProps()} />
            
            {!file ? (
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
              <div className="flex flex-col items-center gap-3">
                <FileVideo className="w-12 h-12 text-primary" />
                <p className="font-medium">{file.name}</p>
                <p className="text-sm text-muted-foreground">{(file.size / (1024 * 1024)).toFixed(2)} MB</p>
                
                <div className="flex gap-2 mt-4" onClick={(e) => e.stopPropagation()}>
                  <Button variant="outline" onClick={handleReset}>Cancel</Button>
                </div>
              </div>
            )}
          </div>
          
          {error && <p className="text-destructive text-sm text-center mt-4">{error}</p>}
          
          {file && !isUploading && (
            <div className="mt-6 flex justify-center">
              <Button onClick={handleUpload} className="w-full sm:w-auto" size="lg">
                <Video className="w-4 h-4 mr-2" /> Upload Video
              </Button>
            </div>
          )}
          
          {isUploading && (
            <div className="mt-6 space-y-2">
              <div className="flex justify-between text-sm">
                <span>Uploading...</span>
                <span>{uploadProgress}%</span>
              </div>
              <Progress value={uploadProgress} className="h-2" />
            </div>
          )}
        </Card>
      ) : (
        <UploadStatus videoId={videoId} onUploadAnother={handleReset} />
      )}
    </div>
  );
}
