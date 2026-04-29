import {ChangeDetectionStrategy, Component, inject, input, output, signal} from '@angular/core';
import {NgFor, NgIf} from '@angular/common';
import {MediaService} from '../../../services/media.service';
import {ConfirmUploadResponse, MediaPurpose} from '../../../models/media.model';

interface UploadingFile {
  name: string;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  mediaId?: string;
  error?: string;
}

/**
 * Drag-and-drop photo uploader. Hands back confirmed mediaIds via the
 * {@code mediaUploaded} output. The host keeps track of the array and uses
 * the IDs when creating a post.
 */
@Component({
  selector: 'koval-photo-uploader',
  standalone: true,
  imports: [NgIf, NgFor],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="uploader"
      (dragover)="onDragOver($event)"
      (drop)="onDrop($event)"
      (click)="fileInput.click()"
    >
      <input
        #fileInput
        type="file"
        hidden
        multiple
        accept="image/jpeg,image/png,image/webp,image/heic"
        (change)="onFiles($event)"
      />
      <p>Click or drop up to {{maxPhotos()}} photo(s) (max 8 MB each)</p>
    </div>
    <ul *ngIf="files().length > 0" class="uploads">
      <li *ngFor="let f of files()">
        <span class="name">{{f.name}}</span>
        <span class="status" [class]="f.status">{{f.status}}</span>
        <span class="error" *ngIf="f.error">{{f.error}}</span>
      </li>
    </ul>
  `,
  styles: [`
    .uploader {
      border: 2px dashed #444;
      padding: 24px;
      text-align: center;
      cursor: pointer;
      border-radius: 8px;
    }
    .uploader:hover { border-color: #666; }
    .uploads { list-style: none; padding: 0; margin-top: 12px; }
    .uploads li { display: flex; gap: 12px; align-items: center; padding: 4px 0; font-size: 0.9em; }
    .name { flex: 1; }
    .status.pending { color: #999; }
    .status.uploading { color: #fa0; }
    .status.done { color: #6c6; }
    .status.failed { color: #f55; }
    .error { color: #f55; }
  `],
})
export class KovalPhotoUploaderComponent {
  readonly purpose = input.required<MediaPurpose>();
  readonly clubId = input<string | null>(null);
  readonly maxPhotos = input<number>(4);

  /** Emitted once per file when its upload+confirm pipeline completes. */
  readonly mediaUploaded = output<ConfirmUploadResponse>();

  readonly files = signal<UploadingFile[]>([]);

  private mediaService = inject(MediaService);

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer?.files) {
      this.handleFiles(Array.from(event.dataTransfer.files));
    }
  }

  onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.handleFiles(Array.from(input.files));
      input.value = '';
    }
  }

  private async handleFiles(picked: File[]): Promise<void> {
    const remaining = this.maxPhotos() - this.files().filter(f => f.status === 'done').length;
    const accepted = picked.slice(0, Math.max(0, remaining));
    for (const file of accepted) {
      const tracker: UploadingFile = {name: file.name, status: 'pending'};
      this.files.update(arr => [...arr, tracker]);
      try {
        this.updateStatus(tracker, 'uploading');
        const result = await this.mediaService.upload(file, {
          purpose: this.purpose(),
          clubId: this.clubId(),
        });
        tracker.mediaId = result.mediaId;
        this.updateStatus(tracker, 'done');
        this.mediaUploaded.emit(result);
      } catch (err) {
        tracker.error = err instanceof Error ? err.message : String(err);
        this.updateStatus(tracker, 'failed');
      }
    }
  }

  private updateStatus(tracker: UploadingFile, status: UploadingFile['status']): void {
    tracker.status = status;
    this.files.update(arr => [...arr]);
  }
}
