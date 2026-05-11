import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs';
import {
  ClubMember,
  ClubService,
  CreateSpotlightData,
  SpotlightBadge,
} from '../../../../../../../services/club.service';
import {KovalMentionInputComponent} from '../../../../../../shared/koval-mention-input/koval-mention-input.component';

const BADGES: SpotlightBadge[] = ['MILESTONE', 'COMEBACK', 'NEW_MEMBER', 'PR', 'GRIT', 'CUSTOM'];
const EXPIRY_OPTIONS = [3, 7, 14];

@Component({
  selector: 'app-spotlight-composer',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, KovalMentionInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="composer-card" data-testid="spotlight-composer">
      <div class="composer-header">
        <h4>{{ 'CLUB_FEED.SPOTLIGHT_COMPOSER_TITLE' | translate }}</h4>
        <button class="composer-close" (click)="cancel.emit()" [attr.aria-label]="'COMMON.CANCEL' | translate">×</button>
      </div>

      <div class="composer-row">
        <label>{{ 'CLUB_FEED.SPOTLIGHT_MEMBER' | translate }}</label>
        <select data-testid="spotlight-member-select" class="composer-input" [(ngModel)]="spotlightedUserId">
          <option value="" disabled>{{ 'CLUB_FEED.SPOTLIGHT_PICK_MEMBER' | translate }}</option>
          @for (m of members$ | async; track m.userId) {
            <option [value]="m.userId">{{ m.displayName }}</option>
          }
        </select>
      </div>

      <div class="composer-row">
        <label>{{ 'CLUB_FEED.SPOTLIGHT_BADGE' | translate }}</label>
        <div class="badge-grid">
          @for (b of badges; track b) {
            <button type="button" class="badge-pill" [class.active]="badge === b"
                    (click)="badge = b">
              {{ ('CLUB_FEED.SPOTLIGHT_BADGE_' + b) | translate }}
            </button>
          }
        </div>
      </div>

      <div class="composer-row">
        <label>{{ 'CLUB_FEED.SPOTLIGHT_TITLE' | translate }}</label>
        <input data-testid="spotlight-title-input" class="composer-input"
               [placeholder]="'CLUB_FEED.SPOTLIGHT_TITLE_PLACEHOLDER' | translate"
               [(ngModel)]="title" maxlength="120" />
      </div>

      <div class="composer-row">
        <label>{{ 'CLUB_FEED.SPOTLIGHT_MESSAGE' | translate }}</label>
        <app-koval-mention-input
          [clubId]="clubId"
          [multiline]="true"
          [rows]="3"
          [placeholder]="'CLUB_FEED.SPOTLIGHT_MESSAGE_PLACEHOLDER' | translate"
          [value]="message"
          (textChange)="message = $event"
          (mentionsChange)="mentionUserIds = $event">
        </app-koval-mention-input>
      </div>

      <div class="composer-row">
        <label>{{ 'CLUB_FEED.SPOTLIGHT_EXPIRES_IN' | translate }}</label>
        <div class="badge-grid">
          @for (d of expiryOptions; track d) {
            <button type="button" class="badge-pill" [class.active]="expiresInDays === d"
                    (click)="expiresInDays = d">
              {{ d }}d
            </button>
          }
        </div>
      </div>

      <div class="composer-actions">
        <button class="composer-cancel" (click)="cancel.emit()">
          {{ 'COMMON.CANCEL' | translate }}
        </button>
        <button data-testid="spotlight-publish-btn" class="btn-primary"
                [disabled]="!canSubmit" (click)="onSubmit()">
          {{ 'CLUB_FEED.SPOTLIGHT_PUBLISH' | translate }}
        </button>
      </div>
    </div>
  `,
  styles: `
    .composer-card { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: var(--radius-md); padding: var(--space-md); display: flex; flex-direction: column; gap: var(--space-sm); }
    .composer-header { display: flex; align-items: center; justify-content: space-between; }
    .composer-header h4 { font-size: var(--text-lg); margin: 0; color: var(--text-color); }
    .composer-close { background: none; border: none; color: var(--text-muted); font-size: 20px; cursor: pointer; line-height: 1; }
    .composer-close:hover { color: var(--text-color); }
    .composer-row { display: flex; flex-direction: column; gap: 4px; }
    .composer-row label { font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-muted); }
    .composer-input {
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
      padding: 6px 10px;
      font-size: var(--text-xs);
      color: var(--text-color);
      outline: none;
    }
    .composer-input:focus { border-color: var(--accent-color); }
    .badge-grid { display: flex; flex-wrap: wrap; gap: 6px; }
    .badge-pill {
      padding: 4px 10px;
      border-radius: 999px;
      border: 1px solid var(--glass-border);
      background: var(--surface-elevated);
      color: var(--text-muted);
      font-size: 10px;
      font-weight: 600;
      letter-spacing: 0.04em;
      cursor: pointer;
    }
    .badge-pill:hover { color: var(--text-color); }
    .badge-pill.active { background: var(--accent-subtle); border-color: var(--accent-color); color: var(--accent-color); }
    .composer-actions { display: flex; gap: 6px; justify-content: flex-end; }
    .composer-cancel { background: none; border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 6px 12px; font-size: var(--text-xs); color: var(--text-muted); cursor: pointer; }
    .composer-cancel:hover { color: var(--text-color); }
    .btn-primary { background: var(--accent-color); color: #000; border: none; border-radius: var(--radius-sm); padding: 6px 12px; font-size: var(--text-xs); font-weight: 600; cursor: pointer; }
    .btn-primary:disabled { opacity: 0.4; cursor: not-allowed; }
    .btn-primary:hover:not(:disabled) { opacity: 0.9; }
  `,
})
export class SpotlightComposerComponent implements OnInit {
  @Input({required: true}) clubId!: string;
  @Output() submitted = new EventEmitter<CreateSpotlightData>();
  @Output() cancel = new EventEmitter<void>();

  private clubService = inject(ClubService);

  members$: Observable<ClubMember[]> = this.clubService.members$;

  spotlightedUserId = '';
  title = '';
  message = '';
  badge: SpotlightBadge = 'MILESTONE';
  expiresInDays = 7;
  mentionUserIds: string[] = [];

  badges = BADGES;
  expiryOptions = EXPIRY_OPTIONS;

  ngOnInit(): void {
    this.clubService.loadMembers(this.clubId);
  }

  get canSubmit(): boolean {
    return !!this.spotlightedUserId && this.title.trim().length > 0;
  }

  onSubmit(): void {
    if (!this.canSubmit) return;
    this.submitted.emit({
      spotlightedUserId: this.spotlightedUserId,
      title: this.title.trim(),
      message: this.message.trim(),
      badge: this.badge,
      mediaIds: [],
      expiresInDays: this.expiresInDays,
      mentionUserIds: this.mentionUserIds,
    });
  }
}
