import {inject, Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {
  ClubTrainingSession,
  CreateRecurringSessionData,
  CreateSessionData,
  RecurringSessionTemplate,
} from '../models/club.model';

@Injectable({ providedIn: 'root' })
export class ClubSessionService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);

  private sessionsSubject = new BehaviorSubject<ClubTrainingSession[]>([]);
  sessions$ = this.sessionsSubject.asObservable();
  private lastSessionsQuery: { clubId: string; from: string; to: string; category?: string } | null = null;

  private openSessionsSubject = new BehaviorSubject<ClubTrainingSession[]>([]);
  openSessions$ = this.openSessionsSubject.asObservable();

  private recurringTemplatesSubject = new BehaviorSubject<RecurringSessionTemplate[]>([]);
  recurringTemplates$ = this.recurringTemplatesSubject.asObservable();

  loadSessions(id: string): void {
    if (this.lastSessionsQuery?.clubId === id) {
      const { from, to, category } = this.lastSessionsQuery;
      this.loadSessionsForRange(id, from, to, category as 'SCHEDULED' | 'OPEN' | undefined);
      return;
    }
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${id}/sessions`)
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.sessionsSubject.next(sessions)));
  }

  loadSessionsForRange(clubId: string, from: string, to: string, category?: 'SCHEDULED' | 'OPEN'): void {
    this.lastSessionsQuery = { clubId, from, to, category };
    const params: Record<string, string> = { from, to };
    if (category) params['category'] = category;
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${clubId}/sessions`, { params })
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.sessionsSubject.next(sessions)));
  }

  getSessionsForClub(clubId: string, from: string, to: string, category?: 'SCHEDULED' | 'OPEN'): Observable<ClubTrainingSession[]> {
    const params: Record<string, string> = { from, to };
    if (category) params['category'] = category;
    return this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${clubId}/sessions`, { params })
      .pipe(catchError(() => of([] as ClubTrainingSession[])));
  }

  loadOpenSessions(clubId: string, from: string, to: string): void {
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${clubId}/sessions`, { params: { from, to, category: 'OPEN' } })
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.openSessionsSubject.next(sessions)));
  }

  loadActivities(clubId: string, from: string, to: string): void {
    this.http
      .get<ClubTrainingSession[]>(`${this.apiUrl}/${clubId}/sessions`, { params: { from, to } })
      .pipe(catchError(() => of([] as ClubTrainingSession[])))
      .subscribe((sessions) => this.ngZone.run(() => this.openSessionsSubject.next(sessions)));
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

  updateSession(clubId: string, sessionId: string, data: CreateSessionData): Observable<ClubTrainingSession> {
    return new Observable((observer) => {
      this.http.put<ClubTrainingSession>(`${this.apiUrl}/${clubId}/sessions/${sessionId}`, data).subscribe({
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

  duplicateSession(clubId: string, sessionId: string, newScheduledAt?: string): Observable<ClubTrainingSession> {
    const url = `${this.apiUrl}/${clubId}/sessions/${sessionId}/duplicate` +
      (newScheduledAt ? `?newScheduledAt=${encodeURIComponent(newScheduledAt)}` : '');
    return new Observable((observer) => {
      this.http.post<ClubTrainingSession>(url, {}).subscribe({
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

  cancelEntireSession(clubId: string, sessionId: string, reason?: string): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/cancel`, { reason: reason || null }).subscribe({
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

  cancelRecurringSessions(clubId: string, templateId: string, reason?: string): Observable<{ cancelledCount: number }> {
    return new Observable((observer) => {
      this.http
        .put<{ cancelledCount: number }>(`${this.apiUrl}/${clubId}/recurring-sessions/${templateId}/cancel-future`, {
          reason: reason || null,
        })
        .subscribe({
          next: (result) => {
            this.ngZone.run(() => {
              this.loadSessions(clubId);
              this.loadRecurringTemplates(clubId);
              observer.next(result);
              observer.complete();
            });
          },
          error: (err) => observer.error(err),
        });
    });
  }

  uploadSessionGpx(clubId: string, sessionId: string, file: File): Observable<ClubTrainingSession> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ClubTrainingSession>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/gpx`, formData);
  }

  deleteSessionGpx(clubId: string, sessionId: string): Observable<ClubTrainingSession> {
    return this.http.delete<ClubTrainingSession>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/gpx`);
  }

  downloadSessionGpx(clubId: string, sessionId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${clubId}/sessions/${sessionId}/gpx`, { responseType: 'blob' });
  }

  linkTrainingToSession(clubId: string, sessionId: string, trainingId: string, clubGroupId?: string): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/link-training`, { trainingId, clubGroupId: clubGroupId || null }).subscribe({
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

  unlinkTrainingFromSession(clubId: string, sessionId: string, clubGroupId?: string): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/sessions/${sessionId}/unlink-training`, { clubGroupId: clubGroupId ?? null }).subscribe({
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

  // --- Recurring Templates ---

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

  updateRecurringTemplateWithInstances(clubId: string, templateId: string, data: CreateRecurringSessionData): Observable<RecurringSessionTemplate> {
    return new Observable((observer) => {
      this.http.put<RecurringSessionTemplate>(`${this.apiUrl}/${clubId}/recurring-sessions/${templateId}/with-instances`, data).subscribe({
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

  resetDetail(): void {
    this.sessionsSubject.next([]);
    this.openSessionsSubject.next([]);
    this.recurringTemplatesSubject.next([]);
  }
}
