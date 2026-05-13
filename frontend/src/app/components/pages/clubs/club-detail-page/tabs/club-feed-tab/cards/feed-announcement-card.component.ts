import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  AnnouncementAttachmentResponse,
  ClubFeedEventResponse,
  ReactionEmoji,
} from '../../../../../../../services/club.service';
import { KovalImageComponent } from '../../../../../../shared/koval-image/koval-image.component';
import { KovalMentionTextComponent } from '../../../../../../shared/koval-mention-text/koval-mention-text.component';
import { FeedReactionBarComponent } from '../../../../../../shared/feed-reaction-bar/feed-reaction-bar.component';
import { FeedCommentsSectionComponent } from './feed-comments-section.component';

@Component({
  selector: 'app-feed-announcement-card',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    KovalImageComponent,
    KovalMentionTextComponent,
    FeedReactionBarComponent,
    FeedCommentsSectionComponent,
  ],
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
        @if (isAuthor && !isEditing) {
          <div class="ann-actions">
            <button class="ann-action-link" (click)="startEdit()">
              {{ 'COMMON.EDIT' | translate }}
            </button>
            <button class="ann-action-link ann-action-link--danger" (click)="onDelete()">
              {{ 'COMMON.DELETE' | translate }}
            </button>
          </div>
        }
      </div>

      @if (isEditing) {
        <textarea
          class="ann-edit-textarea"
          [(ngModel)]="editText"
          (keydown.escape)="cancelEdit()"
          rows="4"
        ></textarea>
        <div class="ann-edit-actions">
          <button class="composer-cancel" (click)="cancelEdit()">
            {{ 'COMMON.CANCEL' | translate }}
          </button>
          <button
            class="btn-primary"
            [disabled]="!editText.trim() || editText.trim() === event.announcementContent"
            (click)="confirmEdit()"
          >
            {{ 'COMMON.SAVE' | translate }}
          </button>
        </div>
      } @else {
        <div class="announcement-content">
          <app-koval-mention-text
            [text]="event.announcementContent ?? ''"
            [mentions]="event.mentionRefs ?? []"
          />
          @if (event.updatedAt && event.updatedAt !== event.createdAt) {
            <span class="ann-edited-tag">Ã‚Â· {{ 'CLUB_FEED.EDITED' | translate }}</span>
          }
        </div>
      }

      @if (!isEditing && imageAttachments().length > 0) {
        <div class="ann-images" [class.ann-images--single]="imageAttachments().length === 1">
          @for (a of imageAttachments(); track a.id) {
            <a
              class="ann-image-link"
              [href]="a.file?.originalUrl"
              target="_blank"
              rel="noopener"
              [attr.aria-label]="a.file?.originalFileName || ''"
            >
              <koval-image [media]="a.file" size="medium" [alt]="a.file?.originalFileName || ''" />
            </a>
          }
        </div>
      }

      @if (!isEditing && fileAttachments().length > 0) {
        <ul class="ann-files">
          @for (a of fileAttachments(); track a.id) {
            <li class="ann-file">
              <span class="ann-file-icon">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5z" />
                  <polyline points="14 2 14 8 20 8" />
                </svg>
              </span>
              <div class="ann-file-info">
                <span class="ann-file-name" [title]="a.file?.originalFileName || ''">
                  {{ a.file?.originalFileName || ('CLUB_FEED.DOWNLOAD' | translate) }}
                </span>
                <span class="ann-file-meta"
                  >{{ formatBytes(a.file?.sizeBytes) }} Ã‚Â·
                  {{ shortType(a.file?.contentType) }}</span
                >
              </div>
              <a
                class="ann-file-dl"
                [href]="a.file?.originalUrl"
                target="_blank"
                rel="noopener"
                [attr.aria-label]="'CLUB_FEED.DOWNLOAD' | translate"
                [download]="a.file?.originalFileName || ''"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7 10 12 15 17 10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </a>
            </li>
          }
        </ul>
      }

      <app-feed-reaction-bar
        [reactions]="event.reactions"
        [currentUserId]="currentUserId"
        (toggled)="reacted.emit({ eventId: event.id, emoji: $event })"
      />

      <app-feed-comments-section
        [clubId]="clubId"
        [eventId]="event.id"
        [comments]="event.comments ?? []"
        [currentUserId]="currentUserId"
        (commentSubmitted)="commentSubmitted.emit($event)"
        (replySubmitted)="replySubmitted.emit($event)"
        (commentEdited)="commentEdited.emit($event)"
        (commentDeleted)="commentDeleted.emit($event)"
        (commentReacted)="commentReacted.emit($event)"
      />
    </div>
  `,
  styles: `
    .feed-card--announcement {
      background: var(--glass-bg);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .announcement-header {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      margin-bottom: var(--space-sm);
    }
    .author-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--surface-elevated);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-muted);
      overflow: hidden;
      flex-shrink: 0;
    }
    .author-avatar img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .author-info {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .author-name {
      font-size: var(--text-sm);
      font-weight: 600;
      color: var(--text-color);
    }
    .author-role {
      font-size: 9px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--accent-color);
    }
    .announcement-time {
      font-size: 9px;
      color: var(--text-muted);
      font-family: monospace;
      white-space: nowrap;
    }
    .announcement-content {
      font-size: var(--text-sm);
      line-height: 1.5;
      color: var(--text-color);
      white-space: pre-wrap;
      word-break: break-word;
    }
    .ann-edited-tag {
      font-size: 9px;
      color: var(--text-muted);
      font-style: italic;
      margin-left: 4px;
    }

    .ann-actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
    .ann-action-link {
      background: none;
      border: none;
      padding: 0;
      font-size: 10px;
      color: var(--text-muted);
      cursor: pointer;
      font-weight: 500;
    }
    .ann-action-link:hover {
      color: var(--accent-color);
    }
    .ann-action-link--danger:hover {
      color: var(--danger-color);
    }

    .ann-edit-textarea {
      width: 100%;
      background: var(--surface-elevated);
      border: none;
      border-radius: var(--radius-sm);
      padding: 8px 10px;
      font-size: var(--text-sm);
      color: var(--text-color);
      outline: none;
      resize: vertical;
      font-family: inherit;
    }
    .ann-edit-textarea:focus {
      border-color: var(--accent-color);
    }
    .ann-edit-actions {
      display: flex;
      gap: 6px;
      justify-content: flex-end;
      margin-top: var(--space-xs);
    }
    .composer-cancel {
      background: none;
      border: none;
      border-radius: var(--radius-sm);
      padding: 6px 12px;
      font-size: var(--text-xs);
      color: var(--text-muted);
      cursor: pointer;
    }
    .composer-cancel:hover {
      color: var(--text-color);
    }
    .btn-primary {
      background: var(--accent-color);
      color: #000;
      border: none;
      border-radius: var(--radius-sm);
      padding: 6px 12px;
      font-size: var(--text-xs);
      font-weight: 600;
      cursor: pointer;
    }
    .btn-primary:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .btn-primary:hover:not(:disabled) {
      opacity: 0.9;
    }

    .ann-images {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 6px;
      margin-top: var(--space-sm);
      border-radius: var(--radius-sm);
      overflow: hidden;
    }
    .ann-images--single {
      grid-template-columns: 1fr;
      max-width: 480px;
    }
    .ann-image-link {
      display: block;
      border-radius: var(--radius-sm);
      overflow: hidden;
      line-height: 0;
    }
    .ann-image-link koval-image {
      display: block;
    }

    .ann-files {
      list-style: none;
      padding: 0;
      margin: var(--space-sm) 0 0;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .ann-file {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
    }
    .ann-file-icon {
      color: var(--accent-color);
      flex-shrink: 0;
      display: inline-flex;
    }
    .ann-file-info {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }
    .ann-file-name {
      font-size: var(--text-xs);
      font-weight: 600;
      color: var(--text-color);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .ann-file-meta {
      font-size: 9px;
      color: var(--text-muted);
      font-family: monospace;
    }
    .ann-file-dl {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--radius-sm);
      color: var(--text-muted);
      text-decoration: none;
      flex-shrink: 0;
    }
    .ann-file-dl:hover {
      background: var(--glass-bg);
      color: var(--accent-color);
    }
  `,
})
export class FeedAnnouncementCardComponent {
  @Input({ required: true }) clubId!: string;
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;

  @Output() commentSubmitted = new EventEmitter<{
    eventId: string;
    content: string;
    mentionUserIds: string[];
  }>();
  @Output() replySubmitted = new EventEmitter<{
    eventId: string;
    parentCommentId: string;
    content: string;
    mentionUserIds: string[];
  }>();
  @Output() commentEdited = new EventEmitter<{
    eventId: string;
    commentId: string;
    content: string;
  }>();
  @Output() commentDeleted = new EventEmitter<{ eventId: string; commentId: string }>();
  @Output() commentReacted = new EventEmitter<{
    eventId: string;
    commentId: string;
    emoji: ReactionEmoji;
  }>();
  @Output() reacted = new EventEmitter<{ eventId: string; emoji: ReactionEmoji }>();
  @Output() announcementEdited = new EventEmitter<{
    eventId: string;
    content: string;
    mediaIds: string[];
  }>();
  @Output() announcementDeleted = new EventEmitter<string>();

  private translate = inject(TranslateService);

  isEditing = false;
  editText = '';

  get isAuthor(): boolean {
    return !!this.currentUserId && this.event.authorId === this.currentUserId;
  }

  startEdit(): void {
    this.editText = this.event.announcementContent ?? '';
    this.isEditing = true;
  }

  cancelEdit(): void {
    this.isEditing = false;
    this.editText = '';
  }

  confirmEdit(): void {
    const content = this.editText.trim();
    if (!content || content === this.event.announcementContent) {
      this.cancelEdit();
      return;
    }
    const mediaIds = (this.event.announcementAttachments ?? [])
      .map((a) => a.file?.mediaId)
      .filter((id): id is string => !!id);
    this.announcementEdited.emit({ eventId: this.event.id, content, mediaIds });
    this.isEditing = false;
  }

  onDelete(): void {
    if (!confirm(this.translate.instant('CLUB_FEED.CONFIRM_DELETE_ANNOUNCEMENT'))) return;
    this.announcementDeleted.emit(this.event.id);
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
