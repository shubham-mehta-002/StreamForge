'use client';

import { useCallback, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { UploadCloud, FileVideo, Video } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { UploadStatus } from './UploadStatus';
import { useVideoUpload } from '@/hooks/useVideoUpload';
import { validateVideoFile } from '@/lib/validateVideoFile';

export function VideoUpload() {
  const [file, setFile] = useState<File | null>(null);
  const { isUploading, uploadProgress, videoId, error, uploadVideo, resetUpload, cancelUpload, setError } = useVideoUpload();

  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (isUploading) return;
      const picked = acceptedFiles[0];
      if (!picked) return;

      const { valid, error: validationError } = validateVideoFile(picked);
      if (!valid) { setError(validationError!); return; }

      setError(null);
      setFile(picked);
    },
    [isUploading, setError],
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'video/*': ['.mp4', '.mov', '.avi', '.mkv', '.webm'] },
    maxFiles: 1,
    disabled: isUploading,
  });

  const handleReset = () => { setFile(null); resetUpload(); };
  const handleCancel = async () => { await cancelUpload(); setFile(null); };

  if (videoId && !isUploading) {
    return <UploadStatus videoId={videoId} onUploadAnother={handleReset} />;
  }

  return (
    <div className="w-full max-w-2xl mx-auto space-y-6">
      <Card className="p-8 border-2 border-dashed hover:border-primary/50 transition-colors">

        {!isUploading && (
          <div
            {...getRootProps()}
            className={`flex flex-col items-center justify-center gap-4 min-h-[250px] cursor-pointer rounded-lg transition-colors ${isDragActive ? 'bg-primary/5' : ''}`}
          >
            <input {...getInputProps()} />

            {!file ? (
              <>
                <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
                  <UploadCloud className="w-8 h-8 text-primary" />
                </div>
                <div className="text-center">
                  <p className="font-medium text-lg">Click to upload or drag and drop</p>
                  <p className="text-muted-foreground text-sm mt-1">MP4, WebM, MOV up to 2 GB</p>
                </div>
              </>
            ) : (
              <div className="flex flex-col items-center gap-3">
                <FileVideo className="w-12 h-12 text-primary" />
                <p className="font-medium text-center break-all">{file.name}</p>
                <p className="text-sm text-muted-foreground">{(file.size / (1024 * 1024)).toFixed(2)} MB</p>
                <div className="flex gap-2 mt-2" onClick={(e) => e.stopPropagation()}>
                  <Button variant="outline" size="sm" onClick={handleReset}>Remove</Button>
                </div>
              </div>
            )}
          </div>
        )}

        {error && <p className="text-destructive text-sm text-center mt-4">{error}</p>}

        {file && !isUploading && (
          <div className="mt-6 flex justify-center">
            <Button onClick={() => uploadVideo(file)} size="lg" className="w-full sm:w-auto">
              <Video className="w-4 h-4 mr-2" /> Upload Video
            </Button>
          </div>
        )}

        {isUploading && (
          <div className="min-h-[250px] flex flex-col items-center justify-center gap-6 px-4">
            <div className="flex flex-col items-center gap-2 text-center">
              <FileVideo className="w-10 h-10 text-primary" />
              <p className="font-medium">{file?.name}</p>
              <p className="text-xs text-muted-foreground">Uploading in parallel chunks directly to S3…</p>
            </div>
            <div className="w-full space-y-2">
              <div className="flex justify-between text-sm font-medium">
                <span>Uploading</span>
                <span>{uploadProgress}%</span>
              </div>
              <Progress value={uploadProgress} className="h-3 rounded-full" />
            </div>
            <Button variant="outline" size="sm" onClick={handleCancel}>Cancel</Button>
          </div>
        )}

      </Card>
    </div>
  );
}
