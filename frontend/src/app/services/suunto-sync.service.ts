import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AuthService, User } from './auth.service';

export interface SuuntoAuthUrlResponse {
  authUrl: string;
}

export interface SuuntoSyncResult {
  totalFetched: number;
  newlyImported: number;
  skippedDuplicates: number;
  skippedErrors: number;
}

@Injectable({ providedIn: 'root' })
export class SuuntoSyncService {
  private readonly suuntoUrl = `${environment.apiUrl}/api/integration/suunto`;
  private readonly authLinkUrl = `${environment.apiUrl}/api/auth/link`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  private importingSubject = new BehaviorSubject<boolean>(false);
  importing$ = this.importingSubject.asObservable();

  getAuthUrl(): Observable<SuuntoAuthUrlResponse> {
    return this.http.get<SuuntoAuthUrlResponse>(`${this.suuntoUrl}/auth`);
  }

  completeOAuth(code: string): Observable<User> {
    const params = new URLSearchParams({ code });
    return this.http
      .post<User>(`${this.suuntoUrl}/callback?${params.toString()}`, {})
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }

  disconnect(): Observable<User> {
    return this.http
      .delete<User>(`${this.authLinkUrl}/suunto`)
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }

  setAutoPush(enabled: boolean): Observable<User> {
    return this.http
      .put<User>(`${this.suuntoUrl}/auto-push`, { enabled })
      .pipe(tap((user) => this.ngZone.run(() => this.authService.updateUser(user))));
  }

  importHistory(): Observable<SuuntoSyncResult> {
    this.importingSubject.next(true);
    return this.http.post<SuuntoSyncResult>(`${this.suuntoUrl}/import-history`, {}).pipe(
      tap({
        next: () => this.ngZone.run(() => this.importingSubject.next(false)),
        error: () => this.ngZone.run(() => this.importingSubject.next(false)),
      }),
    );
  }
}
