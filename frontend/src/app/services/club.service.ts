import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export type ClubVisibility = 'PUBLIC' | 'PRIVATE';
export type ClubMemberRole = 'OWNER' | 'ADMIN' | 'COACH' | 'MEMBER';
export type ClubActivityType =
  | 'MEMBER_JOINED'
  | 'MEMBER_LEFT'
  | 'SESSION_CREATED'
  | 'SESSION_JOINED'
  | 'TRAINING_CREATED'
  | 'RACE_GOAL_ADDED'
  | 'WAITING_LIST_JOINED';

export interface ClubSummary {
  id: string;
  name: string;
  description?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
  memberCount: number;
  membershipStatus?: string; // e.g. "ACTIVE_OWNER", "ACTIVE_MEMBER", "PENDING_MEMBER"
}

export interface ClubDetail {
  id: string;
  name: string;
  description?: string;
  location?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
  memberCount: number;
  ownerId: string;
  currentMembershipStatus?: string;
  currentMemberRole?: ClubMemberRole;
  createdAt: string;
}

export interface ClubMember {
  membershipId?: string;
  userId: string;
  displayName: string;
  profilePicture?: string;
  role: ClubMemberRole;
  joinedAt: string;
  tags?: string[];
}

export interface ClubGroup {
  id: string;
  clubId: string;
  name: string;
  memberIds: string[];
}

export interface MyClubRoleEntry {
  clubId: string;
  clubName: string;
  role: ClubMemberRole;
}

export interface WaitingListEntry {
  userId: string;
  joinedAt: string;
}

export interface ClubTrainingSession {
  id: string;
  clubId: string;
  createdBy: string;
  title: string;
  sport?: string;
  scheduledAt?: string;
  location?: string;
  description?: string;
  linkedTrainingId?: string;
  participantIds: string[];
  createdAt: string;
  recurringTemplateId?: string;
  maxParticipants?: number;
  waitingList?: WaitingListEntry[];
}

export interface RecurringSessionTemplate {
  id: string;
  clubId: string;
  createdBy: string;
  title: string;
  sport?: string;
  dayOfWeek: string;
  timeOfDay: string;
  location?: string;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
  active: boolean;
  createdAt: string;
}

export interface ClubActivity {
  id: string;
  type: ClubActivityType;
  actorId: string;
  actorName?: string;
  targetId?: string;
  targetTitle?: string;
  occurredAt: string;
}

export interface ClubWeeklyStats {
  totalSwimKm: number;
  totalBikeKm: number;
  totalRunKm: number;
  totalSessions: number;
  memberCount: number;
}

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  profilePicture?: string;
  weeklyTss: number;
  sessionCount: number;
  rank: number;
}

export interface ClubRaceGoalResponse {
  goal: {
    id: string;
    title: string;
    sport: string;
    raceDate: string;
    priority: string;
    distance?: string;
    location?: string;
    targetTime?: string;
    notes?: string;
    athleteId: string;
  };
  hasUpcomingClubSession: boolean;
}

export interface CreateClubData {
  name: string;
  description?: string;
  location?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
}

export interface CreateSessionData {
  title: string;
  sport?: string;
  scheduledAt?: string;
  location?: string;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
}

export interface CreateRecurringSessionData {
  title: string;
  sport?: string;
  dayOfWeek: string;
  timeOfDay: string;
  location?: string;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
}

@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);

  private userClubsSubject = new BehaviorSubject<ClubSummary[]>([]);
  userClubs$ = this.userClubsSubject.asObservable();

  private selectedClubSubject = new BehaviorSubject<ClubDetail | null>(null);
  selectedClub$ = this.selectedClubSubject.asObservable();

  private membersSubject = new BehaviorSubject<ClubMember[]>([]);
  members$ = this.membersSubject.asObservable();

  private pendingSubject = new BehaviorSubject<ClubMember[]>([]);
  pending$ = this.pendingSubject.asObservable();

  private feedSubject = new BehaviorSubject<ClubActivity[]>([]);
  feed$ = this.feedSubject.asObservable();

  private sessionsSubject = new BehaviorSubject<ClubTrainingSession[]>([]);
  sessions$ = this.sessionsSubject.asObservable();

  private weeklyStatsSubject = new BehaviorSubject<ClubWeeklyStats | null>(null);
  weeklyStats$ = this.weeklyStatsSubject.asObservable();

  private leaderboardSubject = new BehaviorSubject<LeaderboardEntry[]>([]);
  leaderboard$ = this.leaderboardSubject.asObservable();

  private raceGoalsSubject = new BehaviorSubject<ClubRaceGoalResponse[]>([]);
  raceGoals$ = this.raceGoalsSubject.asObservable();

  private groupsSubject = new BehaviorSubject<ClubGroup[]>([]);
  groups$ = this.groupsSubject.asObservable();

  private myClubRolesSubject = new BehaviorSubject<MyClubRoleEntry[]>([]);
  myClubRoles$ = this.myClubRolesSubject.asObservable();

  private recurringTemplatesSubject = new BehaviorSubject<RecurringSessionTemplate[]>([]);
  recurringTemplates$ = this.recurringTemplatesSubject.asObservable();

  loadUserClubs(): void {
    this.http
      .get<ClubSummary[]>(this.apiUrl)
      .pipe(catchError(() => of([] as ClubSummary[])))
      .subscribe((clubs) => this.ngZone.run(() => this.userClubsSubject.next(clubs)));
  }

  browsePublicClubs(page = 0): Observable<ClubSummary[]> {
    return this.http
      .get<ClubSummary[]>(`${this.apiUrl}/public`, { params: { page: page.toString(), size: '20' } })
      .pipe(catchError(() => of([] as ClubSummary[])));
  }

  createClub(data: CreateClubData): Observable<ClubSummary> {
    return new Observable((observer) => {
      this.http.post<ClubSummary>(this.apiUrl, data).subscribe({
        next: (club) => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            observer.next(club);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadClubDetail(id: string): void {
    this.selectedClubSubject.next(null);
    this.http
      .get<ClubDetail>(`${this.apiUrl}/${id}`)
      .pipe(catchError(() => of(null as ClubDetail | null)))
      .subscribe((club) => this.ngZone.run(() => this.selectedClubSubject.next(club)));
  }

  joinClub(id: string): Observable<void> {
    return new Observable((observer) => {
      this.http.post<void>(`${this.apiUrl}/${id}/join`, {}).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            this.loadClubDetail(id);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  leaveClub(id: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${id}/leave`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            this.loadClubDetail(id);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadMembers(id: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${id}/members`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((members) => this.ngZone.run(() => this.membersSubject.next(members)));
  }

  loadPendingRequests(id: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${id}/members/pending`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((pending) => this.ngZone.run(() => this.pendingSubject.next(pending)));
  }

  approveMember(clubId: string, membershipId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.post<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/approve`, {}).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadMembers(clubId);
            this.loadPendingRequests(clubId);
            this.loadClubDetail(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  rejectMember(clubId: string, membershipId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/reject`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadPendingRequests(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadFeed(id: string): void {
    this.http
      .get<ClubActivity[]>(`${this.apiUrl}/${id}/feed`, { params: { page: '0', size: '50' } })
      .pipe(catchError(() => of([] as ClubActivity[])))
      .subscribe((feed) => this.ngZone.run(() => this.feedSubject.next(feed)));
  }

  loadSessions(id: string): void {
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${id}/sessions`)
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.sessionsSubject.next(sessions)));
  }

  createSession(clubId: string, data: CreateSessionData): Observable<ClubTrainingSession> {
    return new Observable((observer) => {
      this.http.post<ClubTrainingSession>(`${this.apiUrl}/${clubId}/sessions`, data).subscribe({
        next: (session) => {
          this.ngZone.run(() => {
            this.loadSessions(clubId);
            observer.next(session);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  joinSession(clubId: string, sessionId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.post<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/join`, {}).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadSessions(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  cancelSession(clubId: string, sessionId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/join`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadSessions(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadWeeklyStats(id: string): void {
    this.http
      .get<ClubWeeklyStats>(`${this.apiUrl}/${id}/stats/weekly`)
      .pipe(catchError(() => of(null as ClubWeeklyStats | null)))
      .subscribe((stats) => this.ngZone.run(() => this.weeklyStatsSubject.next(stats)));
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

  loadGroups(clubId: string): void {
    this.http
      .get<ClubGroup[]>(`${this.apiUrl}/${clubId}/groups`)
      .pipe(catchError(() => of([] as ClubGroup[])))
      .subscribe((groups) => this.ngZone.run(() => this.groupsSubject.next(groups)));
  }

  createGroup(clubId: string, name: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups`, { name }).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  deleteGroup(clubId: string, groupId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/groups/${groupId}`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  addMemberToGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`, {}).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  removeMemberFromGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.delete<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  updateMemberRole(clubId: string, membershipId: string, role: ClubMemberRole): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/role`, { role }).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadMembers(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadMyClubRoles(): void {
    this.http
      .get<MyClubRoleEntry[]>(`${this.apiUrl}/my-roles`)
      .pipe(catchError(() => of([] as MyClubRoleEntry[])))
      .subscribe((roles) => this.ngZone.run(() => this.myClubRolesSubject.next(roles)));
  }

  loadSessionsForRange(clubId: string, from: string, to: string): void {
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${clubId}/sessions`, { params: { from, to } })
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.sessionsSubject.next(sessions)));
  }

  loadRecurringTemplates(clubId: string): void {
    this.http
      .get<RecurringSessionTemplate[]>(`${this.apiUrl}/${clubId}/recurring-sessions`)
      .pipe(catchError(() => of([] as RecurringSessionTemplate[])))
      .subscribe((templates) => this.ngZone.run(() => this.recurringTemplatesSubject.next(templates)));
  }

  createRecurringTemplate(clubId: string, data: CreateRecurringSessionData): Observable<RecurringSessionTemplate> {
    return new Observable((observer) => {
      this.http.post<RecurringSessionTemplate>(`${this.apiUrl}/${clubId}/recurring-sessions`, data).subscribe({
        next: (template) => {
          this.ngZone.run(() => {
            this.loadRecurringTemplates(clubId);
            this.loadSessions(clubId);
            observer.next(template);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  updateRecurringTemplate(clubId: string, templateId: string, data: CreateRecurringSessionData): Observable<RecurringSessionTemplate> {
    return new Observable((observer) => {
      this.http.put<RecurringSessionTemplate>(`${this.apiUrl}/${clubId}/recurring-sessions/${templateId}`, data).subscribe({
        next: (template) => {
          this.ngZone.run(() => {
            this.loadRecurringTemplates(clubId);
            observer.next(template);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  deleteRecurringTemplate(clubId: string, templateId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/recurring-sessions/${templateId}`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadRecurringTemplates(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  linkTrainingToSession(clubId: string, sessionId: string, trainingId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/link-training`, { trainingId }).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadSessions(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  resetDetail(): void {
    this.selectedClubSubject.next(null);
    this.membersSubject.next([]);
    this.pendingSubject.next([]);
    this.feedSubject.next([]);
    this.sessionsSubject.next([]);
    this.weeklyStatsSubject.next(null);
    this.leaderboardSubject.next([]);
    this.raceGoalsSubject.next([]);
    this.groupsSubject.next([]);
    this.recurringTemplatesSubject.next([]);
  }
}
