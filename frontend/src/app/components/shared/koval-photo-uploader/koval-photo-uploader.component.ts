import {ChangeDetectionStrategy, Component, inject, input, output, signal} from '@angular/core';
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
    @if (files().length > 0) {
      <ul class="uploads">
        @for (f of files(); track f.name) {
          <li>
            <span class="name">{{f.name}}</span>
            <span class="status" [class]="f.status">{{f.status}}</span>
            @if (f.error) {
              <span class="error">{{f.error}}</span>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .uploader {
      border: 2px dashed var(--glass-border);
      padding: var(--space-lg);
      text-align: center;
      cursor: pointer;
      border-radius: var(--radius-md);
      color: var(--text-muted);
    }
    .uploader:hover { border-color: var(--accent-color); color: var(--text-color); }
    .uploads { list-style: none; padding: 0; margin-top: var(--space-sm); }
    .uploads li { display: flex; gap: var(--space-sm); align-items: center; padding: 4px 0; font-size: 0.9em; }
    .name { flex: 1; color: var(--text-color); }
    .status.pending { color: var(--text-muted); }
    .status.uploading { color: var(--dev-accent-color); }
    .status.done { color: var(--success-color); }
    .status.failed { color: var(--danger-color); }
    .error { color: var(--danger-color); }
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
