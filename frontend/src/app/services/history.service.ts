import {DestroyRef, inject, Injectable} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {BehaviorSubject, Observable} from 'rxjs';
import {filter, map, tap} from 'rxjs/operators';
import {HttpClient, HttpParams} from '@angular/common/http';
import {SessionSummary} from './workout-execution.service';
import {AuthService} from './auth.service';
import {FitExportService} from './fit-export.service';
import {MetricsService} from './metrics.service';
import {environment} from '../../environments/environment';

export interface SavedSession extends SessionSummary {
    id: string;
    date: Date;
    syncedToStrava: boolean;
    syncedToGarmin: boolean;
    tss?: number;
    intensityFactor?: number;
    fitFileId?: string;
    rpe?: number;
    scheduledWorkoutId?: string;
    clubSessionId?: string;
    stravaActivityId?: string;
    nolioActivityId?: string;
}

export interface SessionFilters {
    sport?: string | null;
    from?: string | null;
    to?: string | null;
    durationMinSec?: number | null;
    durationMaxSec?: number | null;
    tssMin?: number | null;
    tssMax?: number | null;
}

export interface HistoryState {
    sessions: SavedSession[];
    oldestWindowStart: string | null;
    hasMore: boolean;
    loading: boolean;
    filters: SessionFilters;
}

interface SessionWindowResponse {
    sessions: any[];
    windowStart: string;
    windowEnd: string;
    hasMore: boolean;
}

const DEFAULT_WEEKS = 8;

const EMPTY_FILTERS: SessionFilters = {};

const initialHistoryState: HistoryState = {
    sessions: [],
    oldestWindowStart: null,
    hasMore: false,
    loading: true,
    filters: EMPTY_FILTERS,
};

@Injectable({
    providedIn: 'root',
})
export class HistoryService {
    private readonly apiUrl = `${environment.apiUrl}/api/sessions`;
    private http = inject(HttpClient);
    private authService = inject(AuthService);
    private fitExport = inject(FitExportService);
    private metricsService = inject(MetricsService);
    private destroyRef = inject(DestroyRef);

    /**
     * Most-recent unfiltered window of sessions (default {@link DEFAULT_WEEKS}).
     * Drives dashboard widgets that compute against the current/previous week.
     * Updated only on initial load, save, delete, and patch — never on filter changes.
     */
    private sessionsSubject = new BehaviorSubject<SavedSession[]>([]);
    sessions$ = this.sessionsSubject.asObservable();

    private selectedSessionSubject = new BehaviorSubject<SavedSession | null>(null);
    selectedSession$ = this.selectedSessionSubject.asObservable();

    private loadingSubject = new BehaviorSubject<boolean>(true);
    loading$ = this.loadingSubject.asObservable();

    /**
     * Paginated, filterable view used by the workout-history page. Independent of
     * {@link sessions$} so applying a filter here does not affect dashboard metrics.
     */
    private historyStateSubject = new BehaviorSubject<HistoryState>(initialHistoryState);
    historyState$ = this.historyStateSubject.asObservable();
    historySessions$ = this.historyState$.pipe(map((s) => s.sessions));

    constructor() {
        this.authService.user$.pipe(
            filter((u) => !!u),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(() => this.loadInitialWindow());
    }

    reload(): void {
        this.loadInitialWindow();
    }

    /**
     * Fetches the most recent unfiltered window and re-seeds both {@link sessions$}
     * and {@link historyState$}. Any active filters are cleared.
     */
    private loadInitialWindow(): void {
        this.loadingSubject.next(true);
        this.patchHistory({ loading: true });

        this.http.get<SessionWindowResponse>(`${this.apiUrl}/window`, {
            params: this.buildParams(EMPTY_FILTERS, null, DEFAULT_WEEKS),
        }).subscribe({
            next: (resp) => {
                const parsed = resp.sessions.map(this.parseSession);
                this.sessionsSubject.next(parsed);
                this.historyStateSubject.next({
                    sessions: parsed,
                    oldestWindowStart: resp.windowStart,
                    hasMore: resp.hasMore,
                    loading: false,
                    filters: EMPTY_FILTERS,
                });
                this.refreshSelected(parsed);
                this.loadingSubject.next(false);
            },
            error: () => {
                this.patchHistory({ loading: false });
                this.loadingSubject.next(false);
            },
        });
    }

    /**
     * Applies new filters to the history view: discards the current accumulated list
     * and fetches a fresh first window. Does not touch {@link sessions$}.
     */
    setHistoryFilters(filters: SessionFilters): void {
        const normalized = this.normalizeFilters(filters);
        this.patchHistory({
            sessions: [],
            oldestWindowStart: null,
            hasMore: false,
            loading: true,
            filters: normalized,
        });
        this.http.get<SessionWindowResponse>(`${this.apiUrl}/window`, {
            params: this.buildParams(normalized, null, DEFAULT_WEEKS),
        }).subscribe({
            next: (resp) => {
                const parsed = resp.sessions.map(this.parseSession);
                this.patchHistory({
                    sessions: parsed,
                    oldestWindowStart: resp.windowStart,
                    hasMore: resp.hasMore,
                    loading: false,
                });
            },
            error: () => this.patchHistory({ loading: false }),
        });
    }

    /**
     * Fetches the next-older window and appends it to the history view. No-op while
     * a fetch is in flight or when the backend has signalled no more data.
     */
    loadOlderHistory(): void {
        const state = this.historyStateSubject.value;
        if (state.loading || !state.hasMore || !state.oldestWindowStart) return;

        this.patchHistory({ loading: true });
        this.http.get<SessionWindowResponse>(`${this.apiUrl}/window`, {
            params: this.buildParams(state.filters, state.oldestWindowStart, DEFAULT_WEEKS),
        }).subscribe({
            next: (resp) => {
                const parsed = resp.sessions.map(this.parseSession);
                this.patchHistory({
                    sessions: [...this.historyStateSubject.value.sessions, ...parsed],
                    oldestWindowStart: resp.windowStart,
                    hasMore: resp.hasMore,
                    loading: false,
                });
            },
            error: () => this.patchHistory({ loading: false }),
        });
    }

    selectSession(session: SavedSession | null) {
        this.selectedSessionSubject.next(session);
    }

    setSelectedSession(session: SavedSession) {
        this.selectedSessionSubject.next(session);
    }

    saveSession(summary: SessionSummary, fitBuffer?: ArrayBuffer): void {
        const payload = {
            title: summary.title,
            totalDurationSeconds: summary.totalDuration,
            avgPower: summary.avgPower,
            avgHR: summary.avgHR,
            avgCadence: summary.avgCadence,
            avgSpeed: summary.avgSpeed,
            sportType: summary.sportType,
            blockSummaries: summary.blockSummaries,
            scheduledWorkoutId: summary.scheduledWorkoutId
        };

        this.http.post<any>(this.apiUrl, payload).subscribe({
            next: (saved) => {
                const session: SavedSession = {
                    ...summary,
                    id: saved.id,
                    date: new Date(saved.completedAt),
                    syncedToStrava: false,
                    syncedToGarmin: false,
                    history: [],
                    tss: saved.tss ?? undefined,
                    intensityFactor: saved.intensityFactor ?? undefined,
                    fitFileId: saved.fitFileId ?? undefined,
                };
                this.sessionsSubject.next([session, ...this.sessionsSubject.value]);
                const state = this.historyStateSubject.value;
                if (this.matchesFilters(session, state.filters)) {
                    this.patchHistory({ sessions: [session, ...state.sessions] });
                }
                this.selectedSessionSubject.next(session);

                let bufferToUpload: ArrayBuffer | null = fitBuffer ?? null;
                if (!bufferToUpload && summary.history && summary.history.length > 0) {
                    try {
                        bufferToUpload = this.fitExport.buildFit(summary, new Date(saved.completedAt));
                    } catch (e) {
                        // FIT generation failed — non-fatal
                    }
                }
                if (bufferToUpload) {
                    this.metricsService.uploadFit(saved.id, bufferToUpload).subscribe({
                        next: () => this.loadInitialWindow(),
                        error: () => { },
                    });
                }
            },
            error: () => {
                const session: SavedSession = {
                    ...summary,
                    id: `session_${Date.now()}`,
                    date: new Date(),
                    syncedToStrava: false,
                    syncedToGarmin: false,
                };
                this.sessionsSubject.next([session, ...this.sessionsSubject.value]);
            },
        });
    }

    deleteSession(id: string): void {
        this.http.delete(`${this.apiUrl}/${id}`).subscribe({
            next: () => this.removeLocally(id),
            error: () => this.removeLocally(id),
        });
    }

    private removeLocally(id: string): void {
        this.sessionsSubject.next(this.sessionsSubject.value.filter((s) => s.id !== id));
        const state = this.historyStateSubject.value;
        this.patchHistory({ sessions: state.sessions.filter((s) => s.id !== id) });
        if (this.selectedSessionSubject.value?.id === id) {
            this.selectedSessionSubject.next(null);
        }
    }

    updateSession(id: string, updates: Partial<SavedSession>): Observable<SavedSession> {
        return this.http.patch<SavedSession>(`${this.apiUrl}/${id}`, updates).pipe(
            tap((updated) => {
                const apply = (s: SavedSession) => s.id === id ? { ...s, ...updated } : s;
                this.sessionsSubject.next(this.sessionsSubject.value.map(apply));
                const state = this.historyStateSubject.value;
                this.patchHistory({ sessions: state.sessions.map(apply) });
                if (this.selectedSessionSubject.value?.id === id) {
                    this.selectedSessionSubject.next({ ...this.selectedSessionSubject.value, ...updated });
                }
            })
        );
    }

    private parseSession = (s: any): SavedSession => ({
        title: s.title,
        totalDuration: s.totalDurationSeconds,
        avgPower: s.avgPower,
        avgHR: s.avgHR,
        avgCadence: s.avgCadence,
        avgSpeed: s.avgSpeed || 0,
        blockSummaries: s.blockSummaries || [],
        history: [],
        sportType: s.sportType,
        id: s.id,
        date: new Date(s.completedAt),
        syncedToStrava: false,
        syncedToGarmin: false,
        tss: s.tss ?? undefined,
        intensityFactor: s.intensityFactor ?? undefined,
        fitFileId: s.fitFileId ?? undefined,
        rpe: s.rpe ?? undefined,
        scheduledWorkoutId: s.scheduledWorkoutId ?? undefined,
        clubSessionId: s.clubSessionId ?? undefined,
        stravaActivityId: s.stravaActivityId ?? undefined,
        nolioActivityId: s.nolioActivityId ?? undefined,
    });

    private buildParams(filters: SessionFilters, before: string | null, weeks: number): HttpParams {
        let params = new HttpParams().set('weeks', String(weeks));
        if (before) params = params.set('before', before);
        if (filters.sport) params = params.set('sport', filters.sport);
        if (filters.from) params = params.set('from', filters.from);
        if (filters.to) params = params.set('to', filters.to);
        if (filters.durationMinSec != null) params = params.set('durationMinSec', String(filters.durationMinSec));
        if (filters.durationMaxSec != null) params = params.set('durationMaxSec', String(filters.durationMaxSec));
        if (filters.tssMin != null) params = params.set('tssMin', String(filters.tssMin));
        if (filters.tssMax != null) params = params.set('tssMax', String(filters.tssMax));
        return params;
    }

    private normalizeFilters(f: SessionFilters): SessionFilters {
        return {
            sport: f.sport ?? null,
            from: f.from || null,
            to: f.to || null,
            durationMinSec: f.durationMinSec ?? null,
            durationMaxSec: f.durationMaxSec ?? null,
            tssMin: f.tssMin ?? null,
            tssMax: f.tssMax ?? null,
        };
    }

    /**
     * Lightweight filter-match used when a freshly-saved session is prepended to the
     * history view. Skips duration/tss because TSS may be coach-computed asynchronously.
     */
    private matchesFilters(s: SavedSession, f: SessionFilters): boolean {
        if (f.sport && s.sportType !== f.sport) return false;
        if (f.from && new Date(s.date) < new Date(f.from + 'T00:00:00')) return false;
        if (f.to && new Date(s.date) > new Date(f.to + 'T23:59:59')) return false;
        return true;
    }

    private patchHistory(patch: Partial<HistoryState>): void {
        this.historyStateSubject.next({ ...this.historyStateSubject.value, ...patch });
    }

    private refreshSelected(parsed: SavedSession[]): void {
        const current = this.selectedSessionSubject.value;
        if (current) {
            const refreshed = parsed.find((s) => s.id === current.id);
            if (refreshed) this.selectedSessionSubject.next(refreshed);
        }
    }
}
