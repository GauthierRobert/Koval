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
  FeedCommentEntry,
  KudosResponse,
  LeaderboardEntry,
} from '../models/club.model';

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

  createAnnouncement(clubId: string, content: string): Observable<ClubFeedEventResponse> {
    return this.http.post<ClubFeedEventResponse>(`${this.apiUrl}/${clubId}/feed/announcements`, { content });
  }

  giveKudos(clubId: string, eventId: string): Observable<KudosResponse> {
    return this.http.post<KudosResponse>(`${this.apiUrl}/${clubId}/feed/${eventId}/kudos`, {});
  }

  updateFeedEventCompletion(
    feedEventId: string,
    completionCount: number,
    latestCompletion: { userId: string; displayName: string; profilePicture?: string },
  ): void {
    const current = this.feedEventsSubject.value;
    if (!current) return;

    const updateEvent = (event: ClubFeedEventResponse): ClubFeedEventResponse => {
      if (event.id !== feedEventId) return event;
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
    };

    this.feedEventsSubject.next({
      ...current,
      pinned: current.pinned.map(updateEvent),
      items: current.items.map(updateEvent),
    });
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
    const current = this.feedEventsSubject.value;
    if (!current) return;
    const updateEvent = (event: ClubFeedEventResponse): ClubFeedEventResponse => {
      if (event.id !== feedEventId) return event;
      return { ...event, kudosGivenBy: [...(event.kudosGivenBy ?? []), userId] };
    };
    this.feedEventsSubject.next({
      ...current,
      pinned: current.pinned.map(updateEvent),
      items: current.items.map(updateEvent),
    });
  }

  addComment(clubId: string, eventId: string, content: string): Observable<FeedCommentEntry> {
    return this.http.post<FeedCommentEntry>(`${this.apiUrl}/${clubId}/feed/${eventId}/comments`, { content });
  }

  updateFeedEventComment(feedEventId: string, comment: FeedCommentEntry): void {
    const current = this.feedEventsSubject.value;
    if (!current) return;

    const updateEvent = (event: ClubFeedEventResponse): ClubFeedEventResponse => {
      if (event.id !== feedEventId) return event;
      const comments = [...(event.comments ?? [])];
      if (!comments.find((c) => c.id === comment.id)) {
        comments.push(comment);
      }
      return { ...event, comments };
    };

    this.feedEventsSubject.next({
      ...current,
      pinned: current.pinned.map(updateEvent),
      items: current.items.map(updateEvent),
    });
  }

  resetDetail(): void {
    this.feedSubject.next([]);
    this.weeklyStatsSubject.next(null);
    this.extendedStatsSubject.next(null);
    this.leaderboardSubject.next([]);
    this.raceGoalsSubject.next([]);
    this.feedEventsSubject.next(null);
  }
}
