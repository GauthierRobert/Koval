import { Component, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { map } from 'rxjs/operators';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { HistoryService, SavedSession } from '../../services/history.service';

import { SessionSummary, BlockSummary } from '../../services/workout-execution.service';
import { FitExportService } from '../../services/fit-export.service';
import { AuthService } from '../../services/auth.service';
import { MetricsService } from '../../services/metrics.service';

// @ts-ignore
import FitParser from 'fit-file-parser';

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule, SportIconComponent],
    templateUrl: './workout-history.component.html',
    styleUrl: './workout-history.component.css',
})
export class WorkoutHistoryComponent {
    @ViewChild('fitInput') fitInputRef!: ElementRef<HTMLInputElement>;

    historyService = inject(HistoryService);
    private fitExport = inject(FitExportService);
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);

    sessions$ = this.historyService.sessions$;

    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? 250));

    importing = false;
    importError = false;

    getTss(session: SavedSession, ftp: number): number {
        if (session.tss != null) return Math.round(session.tss);
        return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
    }

    getIF(session: SavedSession, ftp: number): number {
        if (session.intensityFactor != null) return session.intensityFactor;
        return this.metricsService.computeIF(session.avgPower, ftp);
    }

    onSelect(session: SavedSession): void {
        this.historyService.selectSession(session);
    }

    downloadFit(event: Event, session: SavedSession) {
        event.stopPropagation();
        this.fitExport.exportSession(session, session.date);
    }

    triggerUpload() {
        this.fitInputRef.nativeElement.click();
    }

    async onFileSelected(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        input.value = '';

        this.importing = true;
        this.importError = false;

        try {
            const buffer = await file.arrayBuffer();
            const session = await this.parseFit(file.name, buffer);
            this.historyService.saveSession(session, buffer);
        } catch (e) {
            console.error('Failed to import FIT file', e);
            this.importError = true;
            setTimeout(() => (this.importError = false), 4000);
        } finally {
            this.importing = false;
        }
    }

    private parseFit(fileName: string, buffer: ArrayBuffer): Promise<SessionSummary> {
        return new Promise((resolve, reject) => {
            const parser = new FitParser({ force: true, mode: 'list' });

            parser.parse(buffer, (error: any, data: any) => {
                if (error) {
                    reject(new Error(error));
                    return;
                }

                const session = data.sessions?.[0];
                if (!session) {
                    reject(new Error('No session found in FIT file'));
                    return;
                }

                const sportMap: Record<string, SessionSummary['sportType']> = {
                    cycling: 'CYCLING',
                    running: 'RUNNING',
                    swimming: 'SWIMMING',
                };

                const blockSummaries: BlockSummary[] = (data.laps || []).map(
                    (lap: any, i: number) => ({
                        label: `Lap ${i + 1}`,
                        durationSeconds: Math.round(lap.total_elapsed_time || 0),
                        targetPower: 0,
                        actualPower: Math.round(lap.avg_power || 0),
                        actualCadence: Math.round(lap.avg_cadence || 0),
                        actualHR: Math.round(lap.avg_heart_rate || 0),
                        type: 'STEADY',
                    }),
                );

                const name = fileName
                    .replace(/\.fit$/i, '')
                    .replace(/[_-]+/g, ' ')
                    .trim();

                resolve({
                    title: name || 'Uploaded Session',
                    totalDuration: Math.round(
                        session.total_elapsed_time || session.total_timer_time || 0,
                    ),
                    avgPower: Math.round(session.avg_power || 0),
                    avgHR: Math.round(session.avg_heart_rate || 0),
                    avgCadence: Math.round(session.avg_cadence || 0),
                    avgSpeed: session.avg_speed || 0,
                    sportType: sportMap[session.sport?.toLowerCase()] ?? 'CYCLING',
                    blockSummaries,
                    history: [],
                });
            });
        });
    }

    formatTime(seconds: number): string {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        if (h > 0) return `${h}h ${m}m`;
        return `${m} min`;
    }

    formatDate(date: Date): string {
        return new Date(date).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    }

    getSportUnit(session: SavedSession): string {
        if (session.sportType === 'RUNNING') return '/km';
        if (session.sportType === 'SWIMMING') return '/100m';
        return 'W';
    }
}
