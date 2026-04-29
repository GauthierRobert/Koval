export type MediaPurpose = 'GAZETTE_POST' | 'FEED_POST_ENRICHMENT' | 'ANNOUNCEMENT_ATTACHMENT' | 'AVATAR';

export type MediaProcessingStatus = 'PENDING' | 'READY' | 'FAILED';

export interface MediaVariantResponse {
  url: string;
  contentType: string;
  width: number;
  height: number;
  sizeBytes: number;
}

export interface MediaResponse {
  mediaId: string;
  purpose: MediaPurpose;
  contentType: string;
  /** Original filename if the media was uploaded with one (e.g. "Q1-plan.pdf"). */
  originalFileName: string | null;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  blurHash: string | null;
  processingStatus: MediaProcessingStatus | null;
  originalUrl: string;
  variants: Record<string, MediaVariantResponse>;
  expiresAt: string;
}

export interface RequestUploadUrlRequest {
  purpose: MediaPurpose;
  contentType: string;
  sizeBytes: number;
  clubId?: string | null;
  originalFileName?: string | null;
}

export interface RequestUploadUrlResponse {
  mediaId: string;
  objectName: string;
  signedUrl: string;
  contentType: string;
  expiresAt: string;
}

export interface ConfirmUploadResponse {
  mediaId: string;
  confirmed: boolean;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  processingStatus: MediaProcessingStatus | null;
  blurHash: string | null;
}
