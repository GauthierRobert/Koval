import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';
import {
    computeIF as _computeIF,
    computeTss as _computeTss,
    computeTssFromRpe as _computeTssFromRpe,
    findPeakForm as _findPeakForm,
    projectPmc as _projectPmc,
    projectPmcFromSchedule as _projectPmcFromSchedule,
} from './training-math.util';

// @ts-ignore
import FitParser from 'fit-file-parser';

export interface PmcDataPoint {
    date: string;
    ctl: number;
    atl: number;
    tsb: number;
    dailyTss: number;
    sportTss?: Record<string, number>;
    predicted: boolean;
}

export interface FitRecord {
    timestamp: number;
    power: number;
    heartRate: number;
    cadence: number;
    speed: number;
    distance: number;
    elevation?: number;
}

export interface FitTimerEvent {
    timestamp: number;
    type: 'stop' | 'start';
}

export interface FitLap {
    startTimeSeconds: number;
    totalTimerSeconds: number;
    totalDistanceMeters: number;
    avgHeartRate: number;
    avgCadence: number;
    avgPower: number;
    numLengths: number;
    swimStroke?: string;
}

export interface FitParseResult {
    records: FitRecord[];
    timerEvents: FitTimerEvent[];
    laps: FitLap[];
    totalTimerTime: number;
    totalElapsedTime: number;
}

@Injectable({ providedIn: 'root' })
export class MetricsService {
    private readonly apiUrl = `${environment.apiUrl}/api`;
    private http = inject(HttpClient);

    // ── TSS / IF computation (delegated to training-math.util) ───────────────

    computeIF(avgPower: number, ftp: number): number {
        return _computeIF(avgPower, ftp);
    }

    computeTss(durationSeconds: number, avgPower: number, ftp: number): number {
        return _computeTss(durationSeconds, avgPower, ftp);
    }

    computeTssFromRpe(durationSeconds: number, rpe: number): number {
        return _computeTssFromRpe(durationSeconds, rpe);
    }

    // ── PMC HTTP calls ────────────────────────────────────────────────────────

    getPmc(from: string, to: string): Observable<PmcDataPoint[]> {
        return this.http.get<PmcDataPoint[]>(`${this.apiUrl}/sessions/pmc`, {
            params: { from, to },
        });
    }

    getAthletePmc(athleteId: string, from: string, to: string): Observable<PmcDataPoint[]> {
        return this.http.get<PmcDataPoint[]>(`${this.apiUrl}/coach/athletes/${athleteId}/pmc`, {
            params: { from, to },
        });
    }

    // ── PMC projection (delegated to training-math.util) ─────────────────────

    projectPmc(realData: PmcDataPoint[], dailyTss: number, days: number): PmcDataPoint[] {
        return _projectPmc(realData, dailyTss, days);
    }

    findPeakForm(data: PmcDataPoint[]): { date: string; tsb: number } | null {
        return _findPeakForm(data);
    }

    projectPmcFromSchedule(
        realData: PmcDataPoint[],
        scheduledTss: Map<string, number>,
        days: number,
    ): PmcDataPoint[] {
        return _projectPmcFromSchedule(realData, scheduledTss, days);
    }

    // ── FIT binary upload / download ──────────────────────────────────────────

    uploadFit(sessionId: string, buffer: ArrayBuffer): Observable<void> {
        const blob = new Blob([buffer], { type: 'application/octet-stream' });
        const form = new FormData();
        form.append('file', blob, sessionId + '.fit');
        return this.http.post<void>(`${this.apiUrl}/sessions/${sessionId}/fit`, form);
    }

    downloadStoredFit(sessionId: string): Observable<ArrayBuffer> {
        return this.http.get(`${this.apiUrl}/sessions/${sessionId}/fit`, {
            responseType: 'arraybuffer',
        });
    }

    // ── FIT time-series parse ─────────────────────────────────────────────────

    parseFitTimeSeries(buffer: ArrayBuffer): Promise<FitRecord[]> {
        return this.parseFitFile(buffer).then(r => r.records);
    }

    parseFitFile(buffer: ArrayBuffer): Promise<FitParseResult> {
        return new Promise((resolve, reject) => {
            const parser = new FitParser({ force: true, mode: 'list' });
            parser.parse(buffer, (error: any, data: any) => {
                if (error) {
                    reject(new Error(error));
                    return;
                }
                const records: FitRecord[] = (data.records || []).map((r: any) => ({
                    timestamp: r.timestamp ? Math.round(new Date(r.timestamp).getTime() / 1000) : 0,
                    power: Math.round(r.power || 0),
                    heartRate: Math.round(r.heart_rate || 0),
                    cadence: Math.round(r.cadence || 0),
                    speed: r.speed || 0,
                    distance: r.distance || 0,
                    elevation: r.enhanced_altitude ?? r.altitude ?? undefined,
                }));

                const rawEvents = data.events || [];
                const timerEvents: FitTimerEvent[] = [];
                for (const e of rawEvents) {
                    if (e.event !== 'timer' && e.event !== 0) continue;
                    const ts = e.timestamp ? Math.round(new Date(e.timestamp).getTime() / 1000) : 0;
                    if (!ts) continue;
                    const et = e.event_type;
                    if (et === 'stop_all' || et === 'stop' || et === 4 || et === 1) {
                        timerEvents.push({timestamp: ts, type: 'stop'});
                    } else if (et === 'start' || et === 0) {
                        timerEvents.push({timestamp: ts, type: 'start'});
                    }
                }
                timerEvents.sort((a, b) => a.timestamp - b.timestamp);

                if (rawEvents.length > 0 && timerEvents.length === 0) {
                    console.warn('FIT file has events but no timer events were parsed. Event sample:', rawEvents[0]);
                }

                const laps: FitLap[] = (data.laps || []).map((l: any) => ({
                    startTimeSeconds: l.start_time ? Math.round(new Date(l.start_time).getTime() / 1000) : 0,
                    totalTimerSeconds: Math.round(l.total_timer_time || l.total_elapsed_time || 0),
                    totalDistanceMeters: Math.round(l.total_distance || 0),
                    avgHeartRate: Math.round(l.avg_heart_rate || 0),
                    avgCadence: Math.round(l.avg_cadence || 0),
                    avgPower: Math.round(l.avg_power || 0),
                    numLengths: Math.round(l.num_lengths || 0),
                    swimStroke: l.swim_stroke || undefined,
                }));

                const session = data.sessions?.[0];
                const totalTimerTime = Math.round(session?.total_timer_time || 0);
                const totalElapsedTime = Math.round(session?.total_elapsed_time || 0);

                resolve({records, timerEvents, laps, totalTimerTime, totalElapsedTime});
            });
        });
    }
}
