export interface MultipartInitRequest {
    fileName: string;
    contentType: string;
    partCount: number;
}

export interface PartPresignedUrl {
    partNumber: number;
    url: string;
}

export interface MultipartInitResponse {
    videoId: string;
    uploadId: string;
    s3Key: string;
    presignedUrls: PartPresignedUrl[];
}

export interface PartDetail {
    partNumber: number;
    etag: string;
}
