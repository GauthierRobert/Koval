import { Component, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { HistoryService, SavedSession } from '../../services/history.service';
import { TrainingService } from '../../services/training.service';
import { Observable } from 'rxjs';
import { ProgressionChartComponent } from '../progression-chart/progression-chart.component';
import { SessionSummary, BlockSummary } from '../../services/workout-execution.service';
import { FitExportService } from '../../services/fit-export.service';
import { AuthService } from '../../services/auth.service';
import { MetricsService } from '../../services/metrics.service';

// @ts-ignore
import FitParser from 'fit-file-parser';

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule, SportIconComponent, ProgressionChartComponent],
    templateUrl: './workout-history.component.html',
    styleUrl: './workout-history.component.css',
})
export class WorkoutHistoryComponent {
    @ViewChild('fitInput') fitInputRef!: ElementRef<HTMLInputElement>;

    private historyService = inject(HistoryService);
    private trainingService = inject(TrainingService);
    private fitExport = inject(FitExportService);
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private router = inject(Router);

    sessions$: Observable<SavedSession[]> = this.historyService.sessions$;
    selectedSession$ = this.historyService.selectedSession$;

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

    navigateToAnalysis(event: Event, session: SavedSession): void {
        event.stopPropagation();
        this.router.navigate(['/analysis', session.id]);
    }

    downloadFit(event: Event, session: SavedSession) {
        event.stopPropagation();
        this.fitExport.exportSession(session, session.date);
    }

    onSelect(session: SavedSession) {
        this.trainingService.selectTraining(null);
        this.historyService.selectSession(session);
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
            const session = await this.parseFit(file);
            this.historyService.saveSession(session);
        } catch (e) {
            console.error('Failed to import FIT file', e);
            this.importError = true;
            setTimeout(() => (this.importError = false), 4000);
        } finally {
            this.importing = false;
        }
    }

    private parseFit(file: File): Promise<SessionSummary> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();

            reader.onload = (e) => {
                const buffer = e.target!.result as ArrayBuffer;
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

                    const name = file.name
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
                        sportType: sportMap[session.sport?.toLowerCase()] ?? 'CYCLING',
                        blockSummaries,
                        history: [],
                    });
                });
            };

            reader.onerror = () => reject(new Error('Failed to read file'));
            reader.readAsArrayBuffer(file);
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
