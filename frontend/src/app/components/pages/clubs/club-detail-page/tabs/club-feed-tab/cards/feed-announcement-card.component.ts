import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {AnnouncementAttachmentResponse, ClubFeedEventResponse} from '../../../../../../../services/club.service';
import {KovalImageComponent} from '../../../../../../shared/koval-image/koval-image.component';

@Component({
  selector: 'app-feed-announcement-card',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, KovalImageComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-testid="feed-event" class="feed-card feed-card--announcement">
      <div class="announcement-header">
        <div class="author-avatar">
          @if (event.authorProfilePicture) {
            <img [src]="event.authorProfilePicture" [alt]="event.authorName" />
          } @else {
            {{ event.authorName?.charAt(0)?.toUpperCase() }}
          }
        </div>
        <div class="author-info">
          <span class="author-name">{{ event.authorName }}</span>
          <span class="author-role">{{ 'CLUB_FEED.COACH' | translate }}</span>
        </div>
        <span class="announcement-time">{{ relativeTime(event.createdAt) }}</span>
      </div>
      <div class="announcement-content">{{ event.announcementContent }}</div>

      @if (imageAttachments().length > 0) {
        <div class="ann-images" [class.ann-images--single]="imageAttachments().length === 1">
          @for (a of imageAttachments(); track a.id) {
            <a class="ann-image-link" [href]="a.file?.originalUrl" target="_blank" rel="noopener" [attr.aria-label]="a.file?.originalFileName || ''">
              <koval-image [media]="a.file" size="medium" [alt]="a.file?.originalFileName || ''"></koval-image>
            </a>
          }
        </div>
      }

      @if (fileAttachments().length > 0) {
        <ul class="ann-files">
          @for (a of fileAttachments(); track a.id) {
            <li class="ann-file">
              <span class="ann-file-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
              </span>
              <div class="ann-file-info">
                <span class="ann-file-name" [title]="a.file?.originalFileName || ''">
                  {{ a.file?.originalFileName || ('CLUB_FEED.DOWNLOAD' | translate) }}
                </span>
                <span class="ann-file-meta">{{ formatBytes(a.file?.sizeBytes) }} · {{ shortType(a.file?.contentType) }}</span>
              </div>
              <a class="ann-file-dl" [href]="a.file?.originalUrl" target="_blank" rel="noopener"
                 [attr.aria-label]="'CLUB_FEED.DOWNLOAD' | translate"
                 [download]="a.file?.originalFileName || ''">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </a>
            </li>
          }
        </ul>
      }

      <!-- Comments section -->
      <div class="comments-section">
        <button class="comments-toggle" (click)="commentsExpanded = !commentsExpanded">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          @if (commentCount === 1) {
            {{ 'CLUB_FEED.COMMENT_COUNT_ONE' | translate }}
          } @else {
            {{ 'CLUB_FEED.COMMENT_COUNT' | translate: {count: commentCount} }}
          }
          <svg class="expand-chevron" [class.rotated]="commentsExpanded" width="12" height="12" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>

        @if (commentsExpanded) {
          <div class="comments-list">
            @for (c of event.comments; track c.id) {
              <div class="comment-item">
                <div class="comment-avatar">
                  @if (c.profilePicture) {
                    <img [src]="c.profilePicture" [alt]="c.displayName" />
                  } @else {
                    {{ c.displayName.charAt(0).toUpperCase() }}
                  }
                </div>
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="comment-author">{{ c.displayName }}</span>
                    <span class="comment-time">{{ relativeTime(c.createdAt) }}</span>
                  </div>
                  <div class="comment-text">{{ c.content }}</div>
                </div>
              </div>
            }
          </div>

          <div class="comment-input-row">
            <input
              class="comment-input"
              [placeholder]="'CLUB_FEED.COMMENT_PLACEHOLDER' | translate"
              [(ngModel)]="commentText"
              (keydown.enter)="submitComment()"
            />
            <button class="comment-post-btn" [disabled]="!commentText.trim()" (click)="submitComment()">
              {{ 'CLUB_FEED.COMMENT_POST' | translate }}
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    .feed-card--announcement { background: var(--glass-bg); border-radius: var(--radius-md); padding: var(--space-md); }
    .announcement-header { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-sm); }
    .author-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .author-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .author-info { flex: 1; display: flex; flex-direction: column; }
    .author-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .author-role { font-size: 9px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--primary); }
    .announcement-time { font-size: 9px; color: var(--text-muted); font-family: monospace; white-space: nowrap; }
    .announcement-content { font-size: var(--text-sm); line-height: 1.5; color: var(--text-color); white-space: pre-wrap; word-break: break-word; }

    .ann-images { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: var(--space-sm); border-radius: var(--radius-sm); overflow: hidden; }
    .ann-images--single { grid-template-columns: 1fr; max-width: 480px; }
    .ann-image-link { display: block; border-radius: var(--radius-sm); overflow: hidden; line-height: 0; }
    .ann-image-link koval-image { display: block; }

    .ann-files { list-style: none; padding: 0; margin: var(--space-sm) 0 0; display: flex; flex-direction: column; gap: 6px; }
    .ann-file { display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); }
    .ann-file-icon { color: var(--primary); flex-shrink: 0; display: inline-flex; }
    .ann-file-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .ann-file-name { font-size: var(--text-xs); font-weight: 600; color: var(--text-color); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ann-file-meta { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .ann-file-dl { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: var(--radius-sm); color: var(--text-muted); text-decoration: none; flex-shrink: 0; }
    .ann-file-dl:hover { background: var(--glass-bg); color: var(--primary); }

    .comments-section { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); }
    .comments-toggle { display: flex; align-items: center; gap: 6px; background: none; border: none; color: var(--text-muted); font-size: var(--text-xs); cursor: pointer; padding: 0; }
    .comments-toggle:hover { color: var(--text-color); }
    .expand-chevron { transition: transform 0.2s; }
    .expand-chevron.rotated { transform: rotate(180deg); }
    .comments-list { display: flex; flex-direction: column; gap: 8px; margin-top: var(--space-sm); }
    .comment-item { display: flex; gap: var(--space-sm); }
    .comment-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .comment-body { flex: 1; min-width: 0; }
    .comment-meta { display: flex; align-items: center; gap: var(--space-xs); }
    .comment-author { font-size: var(--text-xs); font-weight: 600; color: var(--text-color); }
    .comment-time { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .comment-text { font-size: var(--text-xs); color: var(--text-color); line-height: 1.4; word-break: break-word; }
    .comment-input-row { display: flex; gap: 6px; margin-top: var(--space-sm); }
    .comment-input { flex: 1; background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 6px 10px; font-size: var(--text-xs); color: var(--text-color); outline: none; }
    .comment-input::placeholder { color: var(--text-muted); }
    .comment-input:focus { border-color: var(--primary); }
    .comment-post-btn { background: var(--primary); color: #000; border: none; border-radius: var(--radius-sm); padding: 6px 12px; font-size: var(--text-xs); font-weight: 600; cursor: pointer; white-space: nowrap; }
    .comment-post-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .comment-post-btn:hover:not(:disabled) { opacity: 0.9; }
  `,
})
export class FeedAnnouncementCardComponent {
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string}>();

  commentText = '';
  commentsExpanded = false;

  get commentCount(): number {
    return this.event.comments?.length ?? 0;
  }

  imageAttachments(): AnnouncementAttachmentResponse[] {
    return (this.event.announcementAttachments ?? []).filter(
      (a) => !!a.file && (a.file.contentType?.startsWith('image/') ?? false),
    );
  }

  fileAttachments(): AnnouncementAttachmentResponse[] {
    return (this.event.announcementAttachments ?? []).filter(
      (a) => !!a.file && !(a.file.contentType?.startsWith('image/') ?? false),
    );
  }

  formatBytes(bytes: number | null | undefined): string {
    if (!bytes && bytes !== 0) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  shortType(contentType: string | null | undefined): string {
    if (!contentType) return '';
    if (contentType === 'application/pdf') return 'PDF';
    if (contentType.includes('wordprocessingml')) return 'DOCX';
    if (contentType === 'application/msword') return 'DOC';
    if (contentType.includes('spreadsheetml')) return 'XLSX';
    if (contentType === 'application/vnd.ms-excel') return 'XLS';
    if (contentType.includes('presentationml')) return 'PPTX';
    if (contentType === 'application/vnd.ms-powerpoint') return 'PPT';
    if (contentType === 'text/csv') return 'CSV';
    if (contentType === 'text/plain') return 'TXT';
    const slash = contentType.lastIndexOf('/');
    return slash >= 0 ? contentType.slice(slash + 1).toUpperCase() : contentType.toUpperCase();
  }

  submitComment(): void {
    const text = this.commentText.trim();
    if (!text) return;
    this.commentSubmitted.emit({eventId: this.event.id, content: text});
    this.commentText = '';
  }

  relativeTime(dateStr?: string): string {
    if (!dateStr) return '';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'now';
    if (mins < 60) return `${mins}m`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h`;
    const days = Math.floor(hours / 24);
    return `${days}d`;
  }
}
