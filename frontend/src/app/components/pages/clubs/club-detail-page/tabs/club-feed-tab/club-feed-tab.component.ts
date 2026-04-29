import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {
  ClubDetail,
  ClubFeedEventResponse,
  ClubFeedResponse,
  ClubTrainingSession,
} from '../../../../../../services/club.service';  // types re-exported from club.service
import {AuthService} from '../../../../../../services/auth.service';
import {ClubFeedSseService} from '../../../../../../services/club-feed-sse.service';
import {ClubFeedService} from '../../../../../../services/club-feed.service';
import {ClubSessionService} from '../../../../../../services/club-session.service';
import {FeedSessionCompletionCardComponent} from './cards/feed-session-completion-card.component';
import {FeedAnnouncementCardComponent} from './cards/feed-announcement-card.component';
import {FeedNextGoalCardComponent} from './cards/feed-next-goal-card.component';
import {FeedRaceCompletionCardComponent} from './cards/feed-race-completion-card.component';
import {FeedSessionsUpcomingComponent} from './cards/feed-sessions-upcoming.component';
import {KovalAttachmentUploaderComponent} from '../../../../../shared/koval-attachment-uploader/koval-attachment-uploader.component';

@Component({
  selector: 'app-club-feed-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    FeedSessionCompletionCardComponent,
    FeedAnnouncementCardComponent,
    FeedNextGoalCardComponent,
    FeedRaceCompletionCardComponent,
    FeedSessionsUpcomingComponent,
    KovalAttachmentUploaderComponent,
  ],
  templateUrl: './club-feed-tab.component.html',
  styleUrl: './club-feed-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubFeedTabComponent implements OnInit, OnDestroy, OnChanges {
  @Input() club!: ClubDetail;

  private clubFeedService = inject(ClubFeedService);
  private clubSessionService = inject(ClubSessionService);
  private authService = inject(AuthService);
  private sseService = inject(ClubFeedSseService);
  private cdr = inject(ChangeDetectorRef);

  feedEvents$ = this.clubFeedService.feedEvents$;
  sessions$ = this.clubSessionService.sessions$;
  raceGoals$ = this.clubFeedService.raceGoals$;

  currentUserId: string | null = null;
  currentPage = 0;
  weekOffset = 0;

  // Announcement composer
  announcementText = '';
  composerExpanded = false;
  announcementMediaIds: string[] = [];
  isCoachOrAdmin = false;

  private subs = new Subscription();

  ngOnInit(): void {
    this.subs.add(
      this.authService.user$.subscribe((u) => {
        this.currentUserId = u?.id ?? null;
        this.cdr.markForCheck();
      }),
    );

    // SSE subscriptions
    this.subs.add(
      this.sseService.onCompletionUpdate$.subscribe((payload) => {
        this.clubFeedService.updateFeedEventCompletion(
          payload.feedEventId,
          payload.completionCount,
          payload.latestCompletion,
        );
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onNewFeedEvent$.subscribe((event) => {
        this.clubFeedService.addFeedEvent(event);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onKudosUpdate$.subscribe((payload) => {
        this.clubFeedService.markKudosGiven(payload.feedEventId, payload.givenByUserId);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onCommentUpdate$.subscribe((payload) => {
        this.clubFeedService.updateFeedEventComment(payload.feedEventId, payload.comment);
        this.cdr.markForCheck();
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['club'] && this.club) {
      this.isCoachOrAdmin =
        this.club.currentMemberRole === 'OWNER' ||
        this.club.currentMemberRole === 'ADMIN' ||
        this.club.currentMemberRole === 'COACH';

      this.clubFeedService.loadFeedEvents(this.club.id);
      this.clubFeedService.loadRaceGoals(this.club.id);
      this.loadSessionsForWeek();
      this.sseService.connect(this.club.id);
    }
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.sseService.disconnect();
  }

  loadMore(feed: ClubFeedResponse): void {
    if (!feed.hasMore) return;
    this.currentPage++;
    this.clubFeedService.loadFeedEvents(this.club.id, this.currentPage);
  }

  onWeekChange(direction: number): void {
    this.weekOffset += direction;
    this.loadSessionsForWeek();
  }

  get weekLabel(): string {
    const {from} = this.getWeekRange();
    const monday = new Date(from);
    if (this.weekOffset === 0) return 'This week';
    return monday.toLocaleDateString('en-US', {month: 'short', day: 'numeric'});
  }

  onJoinSession(sessionId: string): void {
    this.clubSessionService.joinSession(this.club.id, sessionId).subscribe(() => {
      this.loadSessionsForWeek();
    });
  }

  onCancelSession(sessionId: string): void {
    this.clubSessionService.cancelSession(this.club.id, sessionId).subscribe(() => {
      this.loadSessionsForWeek();
    });
  }

  onGiveKudos(eventId: string): void {
    this.clubFeedService.giveKudos(this.club.id, eventId).subscribe({
      next: () => {
        if (this.currentUserId) {
          this.clubFeedService.markKudosGiven(eventId, this.currentUserId);
        }
        this.cdr.markForCheck();
      },
      error: () => this.cdr.markForCheck(),
    });
  }

  submitAnnouncement(): void {
    if (!this.announcementText.trim()) return;
    this.clubFeedService
      .createAnnouncement(this.club.id, this.announcementText.trim(), this.announcementMediaIds)
      .subscribe({
        next: () => {
          this.announcementText = '';
          this.announcementMediaIds = [];
          this.composerExpanded = false;
          this.cdr.markForCheck();
        },
      });
  }

  onAnnouncementAttached(mediaIds: string[]): void {
    this.announcementMediaIds = mediaIds;
  }

  cancelAnnouncement(): void {
    this.composerExpanded = false;
    this.announcementText = '';
    this.announcementMediaIds = [];
  }

  onCommentSubmitted(ev: {eventId: string; content: string}): void {
    this.clubFeedService.addComment(this.club.id, ev.eventId, ev.content).subscribe({
      next: (comment) => {
        this.clubFeedService.updateFeedEventComment(ev.eventId, comment);
        this.cdr.markForCheck();
      },
    });
  }

  trackByEventId(_i: number, event: ClubFeedEventResponse): string {
    return event.id;
  }

  private loadSessionsForWeek(): void {
    const {from, to} = this.getWeekRange();
    this.clubSessionService.loadSessionsForRange(this.club.id, from, to);
  }

  private getWeekRange(): {from: string; to: string} {
    const now = new Date();
    const day = now.getDay();
    const mondayOffset = day === 0 ? -6 : 1 - day;
    const monday = new Date(now);
    monday.setDate(now.getDate() + mondayOffset + this.weekOffset * 7);
    monday.setHours(0, 0, 0, 0);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 7);
    return {from: monday.toISOString(), to: sunday.toISOString()};
  }
}
