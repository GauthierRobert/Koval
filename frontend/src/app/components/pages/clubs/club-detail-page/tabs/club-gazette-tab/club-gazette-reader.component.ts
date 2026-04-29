import {AsyncPipe, DatePipe, NgFor, NgIf} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input, OnChanges} from '@angular/core';
import {Observable} from 'rxjs';
import {ClubGazetteService} from '../../../../../../services/club-gazette.service';
import {ClubGazetteEditionResponse} from '../../../../../../models/club-gazette.model';
import {KovalImageComponent} from '../../../../../shared/koval-image/koval-image.component';

/**
 * Reader for one gazette edition. For a published edition: renders all
 * frozen snapshots + the included posts. For a draft: similar layout but
 * the auto sections will be empty (those only exist post-publish).
 */
@Component({
  selector: 'club-gazette-reader',
  standalone: true,
  imports: [AsyncPipe, DatePipe, NgFor, NgIf, KovalImageComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *ngIf="edition$ | async as edition">
      <header class="reader-hdr">
        <h2>Edition #{{edition.editionNumber}}</h2>
        <p>
          {{edition.periodStart | date:'mediumDate'}} → {{edition.periodEnd | date:'mediumDate'}}
        </p>
        <p *ngIf="edition.publishedAt">Published {{edition.publishedAt | date:'mediumDate'}}</p>
        <a *ngIf="edition.hasPdf" [href]="pdfUrl" target="_blank" rel="noopener">Download PDF</a>
      </header>

      <section *ngIf="edition.statsSnapshot as s" class="stats">
        <h3>Stats</h3>
        <p>{{s.sessionCount}} sessions · {{s.totalHours}} h · {{s.totalTss}} TSS</p>
        <p>{{s.swimKm}} km swim · {{s.bikeKm}} km bike · {{s.runKm}} km run</p>
      </section>

      <section *ngIf="edition.leaderboardSnapshot.length" class="leaderboard">
        <h3>Leaderboard</h3>
        <ol>
          <li *ngFor="let entry of edition.leaderboardSnapshot">
            <strong>{{entry.displayName}}</strong>
            <span>· {{entry.tss}} TSS · {{entry.sessionCount}} sessions</span>
          </li>
        </ol>
      </section>

      <section *ngIf="edition.topSessions.length" class="top-sessions">
        <h3>Top sessions</h3>
        <ul>
          <li *ngFor="let s of edition.topSessions">
            <strong>{{s.title}}</strong>
            <span>· {{s.participantCount}} participants · {{s.date | date:'mediumDate'}}</span>
          </li>
        </ul>
      </section>

      <section *ngIf="edition.mostActiveMembers.length" class="active">
        <h3>Most active</h3>
        <ul>
          <li *ngFor="let m of edition.mostActiveMembers">
            <strong>{{m.displayName}}</strong>
            <span>· {{m.hours}} h · {{m.sessions}} sessions</span>
          </li>
        </ul>
      </section>

      <section *ngIf="edition.milestones.length" class="milestones">
        <h3>Milestones</h3>
        <ul>
          <li *ngFor="let m of edition.milestones">
            <strong>{{m.displayName}}</strong> — {{m.description}}
          </li>
        </ul>
      </section>

      <section *ngIf="(postsState$ | async) as posts" class="posts">
        <h3>Member posts</h3>
        <article *ngFor="let p of posts.posts" class="post">
          <header>
            <strong>{{p.authorDisplayName}}</strong>
            <span class="post-type">{{p.type}}</span>
          </header>
          <h4 *ngIf="p.title">{{p.title}}</h4>
          <p>{{p.content}}</p>
          <div *ngIf="p.photos.length" class="photos">
            <koval-image
              *ngFor="let photo of p.photos"
              [media]="photo"
              size="medium"
              alt=""
            ></koval-image>
          </div>
          <div *ngIf="p.linkedSessionSnapshot as s" class="link-card">
            Session: <strong>{{s.title}}</strong> ({{s.scheduledAt | date:'mediumDate'}})
          </div>
          <div *ngIf="p.linkedRaceGoalSnapshot as r" class="link-card">
            Race: <strong>{{r.title}}</strong> ({{r.raceDate | date:'mediumDate'}})
          </div>
        </article>
        <p *ngIf="!posts.posts.length && posts.othersDraftCount > 0" class="hint">
          {{posts.othersDraftCount}} teammates are also contributing to this draft.
        </p>
      </section>
    </ng-container>
  `,
  styles: [`
    .reader-hdr h2 { margin-bottom: 4px; }
    .reader-hdr p { color: #aaa; margin: 0; }
    section { margin-top: 24px; }
    section h3 { border-bottom: 1px solid #2c2c30; padding-bottom: 4px; }
    .post { background: #1e1e22; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
    .post header { display: flex; justify-content: space-between; }
    .post-type { font-size: 0.7em; color: #aaa; text-transform: uppercase; }
    .photos { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 8px; margin-top: 8px; }
    .link-card { background: #2a2a2e; padding: 8px; border-radius: 6px; margin-top: 8px; font-size: 0.9em; }
    .hint { color: #888; font-style: italic; }
  `],
})
export class ClubGazetteReaderComponent implements OnChanges {
  @Input() clubId!: string;
  @Input() editionId!: string;

  edition$!: Observable<ClubGazetteEditionResponse>;
  pdfUrl = '';

  private gazetteService = inject(ClubGazetteService);
  readonly postsState$ = this.gazetteService.posts$;

  ngOnChanges(): void {
    if (!this.clubId || !this.editionId) return;
    this.edition$ = this.gazetteService.getEdition(this.clubId, this.editionId);
    this.gazetteService.loadPosts(this.clubId, this.editionId);
    this.pdfUrl = this.gazetteService.pdfUrl(this.clubId, this.editionId);
    this.gazetteService.markAsRead(this.clubId, this.editionId).subscribe({error: () => {}});
  }
}
