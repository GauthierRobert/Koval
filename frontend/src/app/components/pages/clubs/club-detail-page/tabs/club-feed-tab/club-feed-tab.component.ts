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
import {FeedSpotlightCardComponent} from './cards/feed-spotlight-card.component';
import {SpotlightComposerComponent} from './cards/spotlight-composer.component';
import {KovalAttachmentUploaderComponent} from '../../../../../shared/koval-attachment-uploader/koval-attachment-uploader.component';
import {KovalMentionInputComponent} from '../../../../../shared/koval-mention-input/koval-mention-input.component';
import {CreateSpotlightData, ReactionEmoji} from '../../../../../../models/club.model';

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
    FeedSpotlightCardComponent,
    SpotlightComposerComponent,
    KovalAttachmentUploaderComponent,
    KovalMentionInputComponent,
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
  announcementMentionIds: string[] = [];
  announcementResetTick = 0;
  isCoachOrAdmin = false;

  // Spotlight composer
  spotlightOpen = false;

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

    this.subs.add(
      this.sseService.onCommentEdited$.subscribe((payload) => {
        this.clubFeedService.replaceFeedEventComment(payload.feedEventId, payload.comment);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onCommentDeleted$.subscribe((payload) => {
        this.clubFeedService.removeFeedEventComment(payload.feedEventId, payload.commentId);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onFeedEventUpdated$.subscribe((event) => {
        this.clubFeedService.replaceFeedEvent(event);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onFeedEventDeleted$.subscribe((payload) => {
        this.clubFeedService.removeFeedEvent(payload.feedEventId);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onReactionUpdate$.subscribe((payload) => {
        this.clubFeedService.applyReactionUpdate(payload);
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.sseService.onCommentReplyAdded$.subscribe((payload) => {
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
      .createAnnouncement(
        this.club.id,
        this.announcementText.trim(),
        this.announcementMediaIds,
        this.announcementMentionIds,
      )
      .subscribe({
        next: () => {
          this.announcementText = '';
          this.announcementMediaIds = [];
          this.announcementMentionIds = [];
          this.announcementResetTick++;
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
    this.announcementMentionIds = [];
    this.announcementResetTick++;
  }

  onCommentSubmitted(ev: {eventId: string; content: string; mentionUserIds: string[]}): void {
    this.clubFeedService
      .addComment(this.club.id, ev.eventId, ev.content, ev.mentionUserIds ?? [])
      .subscribe({
        next: (comment) => {
          this.clubFeedService.updateFeedEventComment(ev.eventId, comment);
          this.cdr.markForCheck();
        },
      });
  }

  onReplySubmitted(ev: {
    eventId: string;
    parentCommentId: string;
    content: string;
    mentionUserIds: string[];
  }): void {
    this.clubFeedService
      .addReply(this.club.id, ev.eventId, ev.parentCommentId, ev.content, ev.mentionUserIds ?? [])
      .subscribe({
        next: (reply) => {
          this.clubFeedService.updateFeedEventComment(ev.eventId, reply);
          this.cdr.markForCheck();
        },
      });
  }

  onEventReacted(ev: {eventId: string; emoji: ReactionEmoji}): void {
    if (!this.currentUserId) return;
    // Optimistic apply: invert based on whether user already reacted with this emoji.
    const userId = this.currentUserId;
    const current = this.clubFeedService['feedEventsSubject'].value;
    const event =
      (current?.pinned ?? []).find((e) => e.id === ev.eventId) ??
      (current?.items ?? []).find((e) => e.id === ev.eventId);
    const alreadyReacted = !!event?.reactions?.[ev.emoji]?.includes(userId);
    this.clubFeedService.applyReactionUpdate({
      feedEventId: ev.eventId,
      commentId: null,
      emoji: ev.emoji,
      count: 0, // recomputed by applyReactionUpdate
      actorUserId: userId,
      added: !alreadyReacted,
    });
    this.clubFeedService
      .toggleEventReaction(this.club.id, ev.eventId, ev.emoji)
      .subscribe({error: () => this.cdr.markForCheck()});
  }

  onCommentReacted(ev: {eventId: string; commentId: string; emoji: ReactionEmoji}): void {
    if (!this.currentUserId) return;
    const userId = this.currentUserId;
    this.clubFeedService
      .toggleCommentReaction(this.club.id, ev.eventId, ev.commentId, ev.emoji)
      .subscribe({
        next: (state) => {
          this.clubFeedService.applyReactionUpdate({
            feedEventId: ev.eventId,
            commentId: ev.commentId,
            emoji: ev.emoji,
            count: state.count,
            actorUserId: userId,
            added: state.userReacted,
          });
          this.cdr.markForCheck();
        },
      });
  }

  // --- Spotlights ---

  openSpotlightComposer(): void {
    this.spotlightOpen = true;
  }

  cancelSpotlightComposer(): void {
    this.spotlightOpen = false;
  }

  submitSpotlight(data: CreateSpotlightData): void {
    this.clubFeedService.createSpotlight(this.club.id, data).subscribe({
      next: () => {
        this.spotlightOpen = false;
        this.cdr.markForCheck();
      },
    });
  }

  onSpotlightDeleted(eventId: string): void {
    this.clubFeedService.deleteSpotlight(this.club.id, eventId).subscribe({
      next: () => {
        this.clubFeedService.removeFeedEvent(eventId);
        this.cdr.markForCheck();
      },
    });
  }

  onCommentEdited(ev: {eventId: string; commentId: string; content: string}): void {
    this.clubFeedService
      .updateComment(this.club.id, ev.eventId, ev.commentId, ev.content)
      .subscribe({
        next: (comment) => {
          this.clubFeedService.replaceFeedEventComment(ev.eventId, comment);
          this.cdr.markForCheck();
        },
      });
  }

  onCommentDeleted(ev: {eventId: string; commentId: string}): void {
    this.clubFeedService.deleteComment(this.club.id, ev.eventId, ev.commentId).subscribe({
      next: () => {
        this.clubFeedService.removeFeedEventComment(ev.eventId, ev.commentId);
        this.cdr.markForCheck();
      },
    });
  }

  onAnnouncementEdited(ev: {eventId: string; content: string; mediaIds: string[]}): void {
    this.clubFeedService
      .updateAnnouncement(this.club.id, ev.eventId, ev.content, ev.mediaIds)
      .subscribe({
        next: (event) => {
          this.clubFeedService.replaceFeedEvent(event);
          this.cdr.markForCheck();
        },
      });
  }

  onAnnouncementDeleted(eventId: string): void {
    this.clubFeedService.deleteAnnouncement(this.club.id, eventId).subscribe({
      next: () => {
        this.clubFeedService.removeFeedEvent(eventId);
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
