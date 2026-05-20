import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AuthService, User } from './auth.service';

export interface PolarAuthUrlResponse {
  authUrl: string;
}

export interface PolarSyncResult {
  totalFetched: number;
  newlyImported: number;
  skippedDuplicates: number;
  skippedErrors: number;
}

export interface PolarPushResult {
  status: string;
  trainingTargetId: string;
}

@Injectable({ providedIn: 'root' })
export class PolarSyncService {
  private readonly polarUrl = `${environment.apiUrl}/api/integration/polar`;
  private readonly authLinkUrl = `${environment.apiUrl}/api/auth/link`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  private importingSubject = new BehaviorSubject<boolean>(false);
  importing$ = this.importingSubject.asObservable();

  private pushingSubject = new BehaviorSubject<string | null>(null);
  pushing$ = this.pushingSubject.asObservable();

  getAuthUrl(): Observable<PolarAuthUrlResponse> {
    return this.http.get<PolarAuthUrlResponse>(`${this.polarUrl}/auth`);
  }

  completeOAuth(code: string): Observable<User> {
    const params = new URLSearchParams({ code });
    return this.http
      .post<User>(`${this.polarUrl}/callback?${params.toString()}`, {})
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }

  disconnect(): Observable<User> {
    return this.http
      .delete<User>(`${this.authLinkUrl}/polar`)
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }

  importHistory(): Observable<PolarSyncResult> {
    this.importingSubject.next(true);
    return this.http.post<PolarSyncResult>(`${this.polarUrl}/import-history`, {}).pipe(
      tap({
        next: () => this.ngZone.run(() => this.importingSubject.next(false)),
        error: () => this.ngZone.run(() => this.importingSubject.next(false)),
      }),
    );
  }

  pushScheduledWorkout(scheduledId: string): Observable<PolarPushResult> {
    this.pushingSubject.next(scheduledId);
    return this.http
      .post<PolarPushResult>(`${this.polarUrl}/push/${scheduledId}`, {})
      .pipe(
        tap({
          next: () => this.ngZone.run(() => this.pushingSubject.next(null)),
          error: () => this.ngZone.run(() => this.pushingSubject.next(null)),
        }),
      );
  }

  setAutoPush(enabled: boolean): Observable<User> {
    return this.http
      .put<User>(`${this.polarUrl}/auto-push`, { enabled })
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }
}
