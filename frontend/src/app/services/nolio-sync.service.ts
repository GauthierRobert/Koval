import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { Training } from '../models/training.model';

export interface NolioAuthUrlResponse {
    authUrl: string;
}

function isAbsoluteUrl(value: string | null | undefined): value is string {
    return !!value && /^https?:\/\//i.test(value);
}

@Injectable({ providedIn: 'root' })
export class NolioSyncService {
    private readonly nolioUrl = `${environment.apiUrl}/api/integration/nolio`;
    private readonly authLinkUrl = `${environment.apiUrl}/api/auth/link`;

    private http = inject(HttpClient);
    private authService = inject(AuthService);
    private ngZone = inject(NgZone);

    private pushingSubject = new BehaviorSubject<string | null>(null);
    pushing$ = this.pushingSubject.asObservable();

    connectWrite(): Observable<NolioAuthUrlResponse> {
        return this.http.get<NolioAuthUrlResponse>(`${this.nolioUrl}/authorize`).pipe(
            tap(({ authUrl }) => {
                if (isAbsoluteUrl(authUrl)) {
                    window.open(authUrl, '_blank', 'width=600,height=700');
                }
            }),
        );
    }

    disconnectWrite(): Observable<void> {
        return this.http.delete<void>(`${this.authLinkUrl}/nolio-write`).pipe(
            tap(() => this.ngZone.run(() => this.authService.refreshUser())),
        );
    }

    pushTraining(trainingId: string): Observable<Training> {
        this.pushingSubject.next(trainingId);
        return this.http.post<Training>(`${this.nolioUrl}/workouts/${trainingId}/push`, {}).pipe(
            tap({
                next: () => this.pushingSubject.next(null),
                error: () => this.pushingSubject.next(null),
            }),
        );
    }

    setAutoSync(enabled: boolean): Observable<void> {
        return this.http.patch<void>(`${this.nolioUrl}/settings`, { autoSyncWorkouts: enabled }).pipe(
            tap(() => this.ngZone.run(() => this.authService.refreshUser())),
        );
    }
}
