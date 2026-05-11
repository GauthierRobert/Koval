import {inject, Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {
  ClubActivity,
  ClubExtendedStats,
  ClubFeedEventResponse,
  ClubFeedResponse,
  ClubRaceGoalResponse,
  ClubWeeklyStats,
  CreateSpotlightData,
  EngagementInsightsResponse,
  FeedCommentEntry,
  KudosResponse,
  LeaderboardEntry,
  MentionSuggestion,
  ReactionEmoji,
  ReactionStateResponse,
  ReactionUpdatePayload,
  UpdateSpotlightData,
} from '../models/club.model';
import {
  appendCommentIfMissing,
  applyReactionUpdateToEvent,
  mapFeedEvents,
  removeCommentById,
  replaceCommentById,
  updateEvent,
} from './club-feed.helpers';

@Injectable({ providedIn: 'root' })
export class ClubFeedService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);

  private feedSubject = new BehaviorSubject<ClubActivity[]>([]);
  feed$ = this.feedSubject.asObservable();

  private weeklyStatsSubject = new BehaviorSubject<ClubWeeklyStats | null>(null);
  weeklyStats$ = this.weeklyStatsSubject.asObservable();

  private extendedStatsSubject = new BehaviorSubject<ClubExtendedStats | null>(null);
  extendedStats$ = this.extendedStatsSubject.asObservable();

  private leaderboardSubject = new BehaviorSubject<LeaderboardEntry[]>([]);
  leaderboard$ = this.leaderboardSubject.asObservable();

  private raceGoalsSubject = new BehaviorSubject<ClubRaceGoalResponse[]>([]);
  raceGoals$ = this.raceGoalsSubject.asObservable();

  private feedEventsSubject = new BehaviorSubject<ClubFeedResponse | null>(null);
  feedEvents$ = this.feedEventsSubject.asObservable();

  private engagementInsightsSubject = new BehaviorSubject<EngagementInsightsResponse | null>(null);
  engagementInsights$ = this.engagementInsightsSubject.asObservable();

  loadFeed(id: string): void {
    this.http
      .get<ClubActivity[]>(`${this.apiUrl}/${id}/feed`, { params: { page: '0', size: '50' } })
      .pipe(catchError(() => of([] as ClubActivity[])))
      .subscribe((feed) => this.ngZone.run(() => this.feedSubject.next(feed)));
  }

  loadWeeklyStats(id: string): void {
    this.http
      .get<ClubWeeklyStats>(`${this.apiUrl}/${id}/stats/weekly`)
      .pipe(catchError(() => of(null as ClubWeeklyStats | null)))
      .subscribe((stats) => this.ngZone.run(() => this.weeklyStatsSubject.next(stats)));
  }

  loadExtendedStats(id: string): void {
    this.http
      .get<ClubExtendedStats>(`${this.apiUrl}/${id}/stats/extended`)
      .pipe(catchError(() => of(null as ClubExtendedStats | null)))
      .subscribe((stats) => this.ngZone.run(() => this.extendedStatsSubject.next(stats)));
  }

  loadLeaderboard(id: string): void {
    this.http
      .get<LeaderboardEntry[]>(`${this.apiUrl}/${id}/leaderboard`)
      .pipe(catchError(() => of([] as LeaderboardEntry[])))
      .subscribe((lb) => this.ngZone.run(() => this.leaderboardSubject.next(lb)));
  }

  loadRaceGoals(id: string): void {
    this.http
      .get<ClubRaceGoalResponse[]>(`${this.apiUrl}/${id}/race-goals`)
      .pipe(catchError(() => of([] as ClubRaceGoalResponse[])))
      .subscribe((goals) => this.ngZone.run(() => this.raceGoalsSubject.next(goals)));
  }

  loadFeedEvents(clubId: string, page = 0, size = 20): void {
    this.http
      .get<ClubFeedResponse>(`${this.apiUrl}/${clubId}/feed`, {
        params: { page: page.toString(), size: size.toString() },
      })
      .pipe(catchError(() => of(null as ClubFeedResponse | null)))
      .subscribe((resp) => {
        this.ngZone.run(() => {
          if (resp && page > 0) {
            const current = this.feedEventsSubject.value;
            if (current) {
              resp = { ...resp, items: [...current.items, ...resp.items] };
            }
          }
          this.feedEventsSubject.next(resp);
        });
      });
  }

  createAnnouncement(
    clubId: string,
    content: string,
    mediaIds: string[] = [],
    mentionUserIds: string[] = [],
  ): Observable<ClubFeedEventResponse> {
    return this.http.post<ClubFeedEventResponse>(`${this.apiUrl}/${clubId}/feed/announcements`, {
      content,
      mediaIds,
      mentionUserIds,
    });
  }

  giveKudos(clubId: string, eventId: string): Observable<KudosResponse> {
    return this.http.post<KudosResponse>(`${this.apiUrl}/${clubId}/feed/${eventId}/kudos`, {});
  }

  updateFeedEventCompletion(
    feedEventId: string,
    completionCount: number,
    latestCompletion: { userId: string; displayName: string; profilePicture?: string },
  ): void {
    this.mutateFeed(updateEvent(feedEventId, (event) => {
      const completions = [...(event.completions ?? [])];
      if (!completions.find((c) => c.userId === latestCompletion.userId)) {
        completions.push({
          userId: latestCompletion.userId,
          displayName: latestCompletion.displayName,
          profilePicture: latestCompletion.profilePicture,
          completedSessionId: '',
          completedAt: new Date().toISOString(),
        });
      }
      return { ...event, completions };
    }));
  }

  addFeedEvent(event: ClubFeedEventResponse): void {
    const current = this.feedEventsSubject.value;
    if (!current) return;
    if (event.pinned) {
      this.feedEventsSubject.next({ ...current, pinned: [event, ...current.pinned] });
    } else {
      this.feedEventsSubject.next({ ...current, items: [event, ...current.items] });
    }
  }

  markKudosGiven(feedEventId: string, userId: string): void {
    this.mutateFeed(updateEvent(feedEventId, (event) => ({
      ...event,
      kudosGivenBy: [...(event.kudosGivenBy ?? []), userId],
    })));
  }

  addComment(
    clubId: string,
    eventId: string,
    content: string,
    mentionUserIds: string[] = [],
  ): Observable<FeedCommentEntry> {
    return this.http.post<FeedCommentEntry>(`${this.apiUrl}/${clubId}/feed/${eventId}/comments`, {
      content,
      mentionUserIds,
    });
  }

  addReply(
    clubId: string,
    eventId: string,
    parentCommentId: string,
    content: string,
    mentionUserIds: string[] = [],
  ): Observable<FeedCommentEntry> {
    return this.http.post<FeedCommentEntry>(
      `${this.apiUrl}/${clubId}/feed/${eventId}/comments/${parentCommentId}/replies`,
      { content, mentionUserIds },
    );
  }

  updateComment(
    clubId: string,
    eventId: string,
    commentId: string,
    content: string,
  ): Observable<FeedCommentEntry> {
    return this.http.put<FeedCommentEntry>(
      `${this.apiUrl}/${clubId}/feed/${eventId}/comments/${commentId}`,
      { content },
    );
  }

  deleteComment(clubId: string, eventId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/feed/${eventId}/comments/${commentId}`);
  }

  updateAnnouncement(
    clubId: string,
    eventId: string,
    content: string,
    mediaIds: string[] = [],
    mentionUserIds: string[] = [],
  ): Observable<ClubFeedEventResponse> {
    return this.http.put<ClubFeedEventResponse>(
      `${this.apiUrl}/${clubId}/feed/announcements/${eventId}`,
      { content, mediaIds, mentionUserIds },
    );
  }

  deleteAnnouncement(clubId: string, eventId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/feed/announcements/${eventId}`);
  }

  replaceFeedEventComment(feedEventId: string, comment: FeedCommentEntry): void {
    this.mutateFeed(updateEvent(feedEventId, (event) => replaceCommentById(event, comment)));
  }

  removeFeedEventComment(feedEventId: string, commentId: string): void {
    this.mutateFeed(updateEvent(feedEventId, (event) => removeCommentById(event, commentId)));
  }

  replaceFeedEvent(event: ClubFeedEventResponse): void {
    this.mutateFeed(updateEvent(event.id, () => event));
  }

  removeFeedEvent(feedEventId: string): void {
    const current = this.feedEventsSubject.value;
    if (!current) return;
    this.feedEventsSubject.next({
      ...current,
      pinned: current.pinned.filter((e) => e.id !== feedEventId),
      items: current.items.filter((e) => e.id !== feedEventId),
    });
  }

  updateFeedEventComment(feedEventId: string, comment: FeedCommentEntry): void {
    this.mutateFeed(updateEvent(feedEventId, (event) => appendCommentIfMissing(event, comment)));
  }

  // --- Reactions ---

  toggleEventReaction(
    clubId: string,
    eventId: string,
    emoji: ReactionEmoji,
  ): Observable<ReactionStateResponse> {
    return this.http.post<ReactionStateResponse>(
      `${this.apiUrl}/${clubId}/feed/${eventId}/reactions`,
      { emoji },
    );
  }

  toggleCommentReaction(
    clubId: string,
    eventId: string,
    commentId: string,
    emoji: ReactionEmoji,
  ): Observable<ReactionStateResponse> {
    return this.http.post<ReactionStateResponse>(
      `${this.apiUrl}/${clubId}/feed/${eventId}/comments/${commentId}/reactions`,
      { emoji },
    );
  }

  /** Apply a reaction delta from SSE or HTTP response onto the in-memory feed. */
  applyReactionUpdate(payload: ReactionUpdatePayload): void {
    this.mutateFeed(updateEvent(payload.feedEventId, (event) => applyReactionUpdateToEvent(event, payload)));
  }

  /** Look up a feed event in the current snapshot (pinned or items). */
  findEvent(eventId: string): ClubFeedEventResponse | undefined {
    const current = this.feedEventsSubject.value;
    if (!current) return undefined;
    return current.pinned.find((e) => e.id === eventId) ?? current.items.find((e) => e.id === eventId);
  }

  /** Optimistically toggle the given reaction on a feed event for `userId`. Returns whether it was added. */
  optimisticToggleEventReaction(eventId: string, emoji: ReactionEmoji, userId: string): boolean {
    const event = this.findEvent(eventId);
    const alreadyReacted = !!event?.reactions?.[emoji]?.includes(userId);
    this.applyReactionUpdate({
      feedEventId: eventId,
      commentId: null,
      emoji,
      count: 0, // recomputed by applyReactionUpdateToEvent via Set semantics
      actorUserId: userId,
      added: !alreadyReacted,
    });
    return !alreadyReacted;
  }

  // --- Mentions ---

  suggestMentions(clubId: string, query: string): Observable<MentionSuggestion[]> {
    return this.http
      .get<MentionSuggestion[]>(`${this.apiUrl}/${clubId}/feed/mentions/suggest`, {
        params: { q: query ?? '' },
      })
      .pipe(catchError(() => of([] as MentionSuggestion[])));
  }

  // --- Spotlights ---

  createSpotlight(clubId: string, data: CreateSpotlightData): Observable<ClubFeedEventResponse> {
    return this.http.post<ClubFeedEventResponse>(
      `${this.apiUrl}/${clubId}/feed/spotlights`,
      data,
    );
  }

  updateSpotlight(
    clubId: string,
    eventId: string,
    data: UpdateSpotlightData,
  ): Observable<ClubFeedEventResponse> {
    return this.http.put<ClubFeedEventResponse>(
      `${this.apiUrl}/${clubId}/feed/spotlights/${eventId}`,
      data,
    );
  }

  deleteSpotlight(clubId: string, eventId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/feed/spotlights/${eventId}`);
  }

  // --- Engagement insights ---

  loadEngagementInsights(clubId: string, days = 30): void {
    this.http
      .get<EngagementInsightsResponse>(`${this.apiUrl}/${clubId}/feed/engagement-insights`, {
        params: { days: days.toString() },
      })
      .pipe(catchError(() => of(null as EngagementInsightsResponse | null)))
      .subscribe((resp) => this.ngZone.run(() => this.engagementInsightsSubject.next(resp)));
  }

  resetDetail(): void {
    this.feedSubject.next([]);
    this.weeklyStatsSubject.next(null);
    this.extendedStatsSubject.next(null);
    this.leaderboardSubject.next([]);
    this.raceGoalsSubject.next([]);
    this.feedEventsSubject.next(null);
    this.engagementInsightsSubject.next(null);
  }

  /** Internal: apply an updater to every event in the in-memory feed. */
  private mutateFeed(updater: (event: ClubFeedEventResponse) => ClubFeedEventResponse): void {
    const current = this.feedEventsSubject.value;
    if (!current) return;
    this.feedEventsSubject.next(mapFeedEvents(current, updater));
  }
}
