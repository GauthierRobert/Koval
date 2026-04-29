import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {DatePipe, SlicePipe} from '@angular/common';
import {ClubGazettePostResponse, GazettePostType} from '../../../../../../../models/club-gazette.model';

const TYPE_LABEL: Record<GazettePostType, string> = {
  REFLECTION: 'Reflection',
  SESSION_RECAP: 'Session recap',
  RACE_RESULT: 'Race result',
  PERSONAL_WIN: 'Personal win',
  SHOUTOUT: 'Shoutout',
};

/**
 * Right rail of the gazette composer: live preview of the post
 * being written, summary stats (words, reading time, photos),
 * and the recent posts already in this draft edition.
 */
@Component({
  selector: 'gazette-composer-aside-right',
  standalone: true,
  imports: [DatePipe, SlicePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="gz2-card preview-card">
      <header>
        <h5>Live preview</h5>
        <small>FEED</small>
      </header>
      <div class="gz2-preview-card">
        <div class="author-row">
          <div class="av">{{authorInitials()}}</div>
          <div class="who">
            <b>{{authorName() || 'You'}}</b>
            <small>just now</small>
          </div>
          <span class="ptype">{{typeLabel()}}</span>
        </div>
        @if (title()) {
          <div class="ptitle">{{title()}}</div>
        } @else {
          <div class="ptitle placeholder">Untitled</div>
        }
        @if (content()) {
          <div class="pcontent">{{content() | slice:0:240}}@if (content().length > 240) {…}</div>
        } @else {
          <div class="pcontent placeholder">
            Start typing in the "Content" field — your post will preview here in real time.
          </div>
        }
      </div>
    </section>

    <section class="gz2-card stats-card">
      <header>
        <h5>Statistics</h5>
        <small>SUMMARY</small>
      </header>
      <div class="gz2-meta-grid">
        <div class="gz2-meta-cell">
          <small>Words</small>
          <b>{{wordCount()}}</b>
        </div>
        <div class="gz2-meta-cell">
          <small>Reading</small>
          <b>{{readingTime()}}</b>
        </div>
        <div class="gz2-meta-cell">
          <small>Photos</small>
          <b [class.warn]="photoCount() === 0">{{photoCount()}} / {{maxPhotos()}}</b>
        </div>
        <div class="gz2-meta-cell">
          <small>Type</small>
          <b class="type-pill">{{typeLabel()}}</b>
        </div>
      </div>
    </section>

    @if (recentPosts().length > 0) {
      <section class="gz2-card recents-card">
        <header>
          <h5>Recent · this edition</h5>
          <small>{{recentPosts().length}}</small>
        </header>
        @for (p of recentPosts().slice(0, 4); track p.id) {
          <div class="gz2-recent-row">
            @if (p.authorProfilePicture) {
              <img class="av img" [src]="p.authorProfilePicture" alt=""/>
            } @else {
              <div class="av">{{authorInitialsOf(p.authorDisplayName)}}</div>
            }
            <div class="nm">
              {{p.title || (p.content | slice:0:48)}}
              <small>{{labelOf(p.type)}} · {{p.createdAt | date:'MMM d'}}</small>
            </div>
            @if (p.photos.length > 0) {
              <span class="tag">{{p.photos.length}} 📷</span>
            }
          </div>
        }
      </section>
    }
  `,
  styleUrls: ['./gazette-composer-aside-right.component.css'],
})
export class GazetteComposerAsideRightComponent {
  readonly title = input<string>('');
  readonly content = input<string>('');
  readonly type = input<GazettePostType>('REFLECTION');
  readonly photoCount = input<number>(0);
  readonly maxPhotos = input<number>(4);
  readonly authorName = input<string>('');
  readonly recentPosts = input<ClubGazettePostResponse[]>([]);

  readonly authorInitials = computed(() => this.authorInitialsOf(this.authorName() || 'You'));
  readonly typeLabel = computed(() => TYPE_LABEL[this.type()] ?? this.type());

  readonly wordCount = computed(() => {
    const text = this.content().trim();
    if (!text) return 0;
    return text.split(/\s+/).filter(w => w.length > 0).length;
  });

  readonly readingTime = computed(() => {
    const w = this.wordCount();
    if (w === 0) return '—';
    const minutes = Math.max(1, Math.round(w / 220));
    return `${minutes} min`;
  });

  authorInitialsOf(name: string): string {
    const parts = name.trim().split(/\s+/);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  labelOf(type: GazettePostType): string {
    return TYPE_LABEL[type] ?? type;
  }
}
