import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { HistoryService } from './history.service';

export interface SyncResult {
    totalFetched: number;
    newlyImported: number;
    skippedDuplicates: number;
    skippedErrors: number;
}

export interface SyncStatus {
    stravaConnected: boolean;
    lastSyncAt: string | null;
    memberSince: string | null;
}

@Injectable({ providedIn: 'root' })
export class StravaSyncService {
    private readonly apiUrl = `${environment.apiUrl}/api/integration/strava`;
    private http = inject(HttpClient);
    private historyService = inject(HistoryService);

    private syncingSubject = new BehaviorSubject<boolean>(false);
    syncing$ = this.syncingSubject.asObservable();

    private lastResultSubject = new BehaviorSubject<SyncResult | null>(null);
    lastResult$ = this.lastResultSubject.asObservable();

    importHistory(): Observable<SyncResult> {
        this.syncingSubject.next(true);
        this.lastResultSubject.next(null);

        return this.http.post<SyncResult>(`${this.apiUrl}/import-history`, {}).pipe(
            tap({
                next: (result) => {
                    this.syncingSubject.next(false);
                    this.lastResultSubject.next(result);
                    if (result.newlyImported > 0) {
                        this.historyService.reload();
                    }
                },
                error: () => this.syncingSubject.next(false),
            }),
        );
    }

    getStatus(): Observable<SyncStatus> {
        return this.http.get<SyncStatus>(`${this.apiUrl}/status`);
    }

    /**
     * Fetch Strava streams and build a FIT file for a session that was imported from Strava.
     * Returns the updated session with fitFileId set.
     */
    buildFit(sessionId: string): Observable<any> {
        return this.http.post(`${this.apiUrl}/sessions/${sessionId}/build-fit`, {}).pipe(
            tap(() => this.historyService.reload()),
        );
    }
}
