import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, Observable} from 'rxjs';
import {environment} from '../../environments/environment';
import {
  ConfirmUploadResponse,
  MediaResponse,
  RequestUploadUrlRequest,
  RequestUploadUrlResponse,
} from '../models/media.model';

/**
 * Upload pipeline (3 round trips):
 *   1. POST /api/media/upload-url      → receive signed URL + mediaId
 *   2. PUT  <signedUrl> (direct GCS)   → upload bytes
 *   3. POST /api/media/{id}/confirm    → server runs variant + BlurHash pipeline
 *
 * Use {@link upload} for the convenience all-in-one wrapper that returns a
 * confirmed mediaId.
 */
@Injectable({providedIn: 'root'})
export class MediaService {
  private readonly apiUrl = `${environment.apiUrl}/api/media`;
  private http = inject(HttpClient);

  requestUploadUrl(req: RequestUploadUrlRequest): Observable<RequestUploadUrlResponse> {
    return this.http.post<RequestUploadUrlResponse>(`${this.apiUrl}/upload-url`, req);
  }

  confirm(mediaId: string): Observable<ConfirmUploadResponse> {
    return this.http.post<ConfirmUploadResponse>(`${this.apiUrl}/${mediaId}/confirm`, {});
  }

  getReadResponse(mediaId: string): Observable<MediaResponse> {
    return this.http.get<MediaResponse>(`${this.apiUrl}/${mediaId}/url`);
  }

  delete(mediaId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${mediaId}`);
  }

  /**
   * One-shot helper: requests a signed URL, PUTs the file directly to GCS,
   * confirms with the backend. Returns the confirmed mediaId.
   */
  async upload(file: File, req: Omit<RequestUploadUrlRequest, 'contentType' | 'sizeBytes' | 'originalFileName'>): Promise<ConfirmUploadResponse> {
    const fullReq: RequestUploadUrlRequest = {
      ...req,
      contentType: file.type,
      sizeBytes: file.size,
      originalFileName: file.name,
    };
    const upload = await firstValueFrom(this.requestUploadUrl(fullReq));
    await this.putToSignedUrl(upload.signedUrl, file);
    return firstValueFrom(this.confirm(upload.mediaId));
  }

  /** Direct PUT to a signed URL. Bypasses the backend; bytes go straight to GCS. */
  private async putToSignedUrl(signedUrl: string, file: File): Promise<void> {
    const res = await fetch(signedUrl, {
      method: 'PUT',
      headers: {'Content-Type': file.type},
      body: file,
    });
    if (!res.ok) {
      throw new Error(`Upload failed: ${res.status} ${res.statusText}`);
    }
  }
}
