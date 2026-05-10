import {ChangeDetectionStrategy, Component, inject, input, output, signal} from '@angular/core';
import {MediaService} from '../../../services/media.service';
import {ConfirmUploadResponse, MediaPurpose} from '../../../models/media.model';

interface AttachmentItem {
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  mediaId?: string;
  error?: string;
}

const DEFAULT_ACCEPT =
  'image/jpeg,image/png,image/webp,image/heic,image/gif,application/pdf,' +
  'application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,' +
  'application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,' +
  'application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,' +
  'text/plain,text/csv';

/**
 * Generic file uploader. Handles images and documents (PDF, doc, xls, ppt, txt, csv).
 *
 * Each picked file goes through the standard 3-step upload pipeline; the host
 * receives confirmed mediaIds via the {@code attached} output.
 */
@Component({
  selector: 'koval-attachment-uploader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kau">
      <button
        type="button"
        class="kau-btn"
        [disabled]="atLimit()"
        (click)="!atLimit() && fileInput.click()"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 17.93 8.8l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.49"/>
        </svg>
        <span>{{ buttonLabel() }}</span>
      </button>
      <input
        #fileInput
        type="file"
        hidden
        multiple
        [attr.accept]="accept()"
        (change)="onFiles($event)"
      />

      @if (files().length > 0) {
        <ul class="kau-list">
          @for (f of files(); track $index; let i = $index) {
            <li class="kau-chip" [class.failed]="f.status === 'failed'">
              <span class="kau-icon" [attr.data-kind]="iconKind(f.contentType)">
                @if (iconKind(f.contentType) === 'image') {
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="18" height="18" x="3" y="3" rx="2"/>
                    <circle cx="9" cy="9" r="2"/>
                    <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>
                  </svg>
                } @else {
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                }
              </span>
              <span class="kau-name" [title]="f.fileName">{{ f.fileName }}</span>
              <span class="kau-size">{{ formatSize(f.sizeBytes) }}</span>
              <span class="kau-status" [class]="f.status">
                @switch (f.status) {
                  @case ('uploading') { â€¦ }
                  @case ('done') { âœ“ }
                  @case ('failed') { ! }
                }
              </span>
              <button type="button" class="kau-remove" (click)="remove(i)" aria-label="Remove">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
                </svg>
              </button>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    .kau { display: flex; flex-direction: column; gap: 8px; }
    .kau-btn {
      display: inline-flex; align-items: center; gap: 6px;
      background: var(--surface-elevated); color: var(--text-color);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
      padding: 6px 12px;
      font-size: var(--text-xs); font-weight: 600;
      cursor: pointer;
      align-self: flex-start;
    }
    .kau-btn:hover:not(:disabled) { border-color: var(--accent-color); color: var(--accent-color); }
    .kau-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .kau-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
    .kau-chip {
      display: flex; align-items: center; gap: 8px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
      padding: 6px 10px;
      font-size: var(--text-xs);
    }
    .kau-chip.failed { border-color: var(--danger-color, #f55); }
    .kau-icon { color: var(--text-muted); flex-shrink: 0; display: inline-flex; }
    .kau-icon[data-kind='image'] { color: var(--accent-color); }
    .kau-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-color); }
    .kau-size { color: var(--text-muted); font-family: monospace; font-size: 9px; flex-shrink: 0; }
    .kau-status { font-family: monospace; font-size: 11px; flex-shrink: 0; }
    .kau-status.uploading { color: var(--text-muted); }
    .kau-status.done { color: var(--success-color, #6c6); }
    .kau-status.failed { color: var(--danger-color, #f55); }
    .kau-remove {
      background: none; border: none; color: var(--text-muted); cursor: pointer;
      padding: 0; display: inline-flex; align-items: center; flex-shrink: 0;
    }
    .kau-remove:hover { color: var(--danger-color, #f55); }
  `],
})
export class KovalAttachmentUploaderComponent {
  readonly purpose = input.required<MediaPurpose>();
  readonly clubId = input<string | null>(null);
  readonly maxFiles = input<number>(5);
  readonly accept = input<string>(DEFAULT_ACCEPT);
  readonly label = input<string>('Attach files');

  /** Emits the live list of confirmed mediaIds whenever it changes. */
  readonly attached = output<string[]>();

  readonly files = signal<AttachmentItem[]>([]);

  private mediaService = inject(MediaService);

  atLimit(): boolean {
    return this.files().filter(f => f.status !== 'failed').length >= this.maxFiles();
  }

  buttonLabel(): string {
    return this.atLimit() ? `${this.label()} (max ${this.maxFiles()})` : this.label();
  }

  iconKind(contentType: string): 'image' | 'doc' {
    return contentType.startsWith('image/') ? 'image' : 'doc';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.handleFiles(Array.from(input.files));
    input.value = '';
  }

  remove(index: number): void {
    this.files.update(arr => arr.filter((_, i) => i !== index));
    this.emitConfirmed();
  }

  /** Reset state â€” host calls this after successfully posting. */
  reset(): void {
    this.files.set([]);
    this.emitConfirmed();
  }

  private async handleFiles(picked: File[]): Promise<void> {
    const remaining = this.maxFiles() - this.files().filter(f => f.status !== 'failed').length;
    const accepted = picked.slice(0, Math.max(0, remaining));
    for (const file of accepted) {
      const tracker: AttachmentItem = {
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
        status: 'uploading',
      };
      this.files.update(arr => [...arr, tracker]);
      try {
        const result: ConfirmUploadResponse = await this.mediaService.upload(file, {
          purpose: this.purpose(),
          clubId: this.clubId(),
        });
        this.updateTracker(tracker, t => {
          t.mediaId = result.mediaId;
          t.status = 'done';
        });
        this.emitConfirmed();
      } catch (err) {
        this.updateTracker(tracker, t => {
          t.status = 'failed';
          t.error = err instanceof Error ? err.message : String(err);
        });
      }
    }
  }

  private emitConfirmed(): void {
    const ids = this.files()
      .filter(f => f.status === 'done' && f.mediaId)
      .map(f => f.mediaId!);
    this.attached.emit(ids);
  }

  private updateTracker(tracker: AttachmentItem, mutate: (t: AttachmentItem) => void): void {
    this.files.update(arr => {
      const idx = arr.indexOf(tracker);
      if (idx === -1) return arr;
      const copy = [...arr];
      const updated = {...tracker};
      mutate(updated);
      copy[idx] = updated;
      return copy;
    });
  }
}
