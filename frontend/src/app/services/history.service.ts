import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { filter } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { SessionSummary } from './workout-execution.service';
import { AuthService } from './auth.service';
import { FitExportService } from './fit-export.service';
import { MetricsService } from './metrics.service';

export interface SavedSession extends SessionSummary {
    id: string;
    date: Date;
    syncedToStrava: boolean;
    syncedToGarmin: boolean;
    tss?: number;
    intensityFactor?: number;
    fitFileId?: string;
}

@Injectable({
    providedIn: 'root',
})
export class HistoryService {
    private readonly apiUrl = 'http://localhost:8080/api/sessions';
    private http = inject(HttpClient);
    private authService = inject(AuthService);
    private fitExport = inject(FitExportService);
    private metricsService = inject(MetricsService);

    private sessionsSubject = new BehaviorSubject<SavedSession[]>([]);
    sessions$ = this.sessionsSubject.asObservable();

    private selectedSessionSubject = new BehaviorSubject<SavedSession | null>(null);
    selectedSession$ = this.selectedSessionSubject.asObservable();

    constructor() {
        this.authService.user$.pipe(filter((u) => !!u)).subscribe(() => this.loadSessions());
    }

    private loadSessions(): void {
        this.http.get<any[]>(this.apiUrl).subscribe({
            next: (sessions) => {
                const parsed: SavedSession[] = sessions.map((s) => ({
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
                }));
                this.sessionsSubject.next(parsed);
            },
            error: () => {},
        });
    }

    selectSession(session: SavedSession | null) {
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

                // Use provided fitBuffer (manual import), otherwise generate from per-second history
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
                        next: () => this.loadSessions(),
                        error: () => {},
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
        if (this.selectedSessionSubject.value?.id === id) {
            this.selectedSessionSubject.next(null);
        }
    }
}
