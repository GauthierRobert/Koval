import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// @ts-ignore
import FitParser from 'fit-file-parser';

export interface PmcDataPoint {
    date: string;          // ISO date string
    ctl: number;
    atl: number;
    tsb: number;
    dailyTss: number;
    predicted: boolean;
}

export interface FitRecord {
    timestamp: number;     // Unix epoch seconds
    power: number;
    heartRate: number;
    cadence: number;
    speed: number;         // m/s
}

@Injectable({ providedIn: 'root' })
export class MetricsService {
    private readonly apiUrl = 'http://localhost:8080/api';
    private http = inject(HttpClient);

    // ── TSS / IF computation ──────────────────────────────────────────────────

    computeIF(avgPower: number, ftp: number): number {
        if (!ftp || ftp <= 0 || !avgPower) return 0;
        return avgPower / ftp;
    }

    computeTss(durationSeconds: number, avgPower: number, ftp: number): number {
        if (!ftp || ftp <= 0 || !avgPower || !durationSeconds) return 0;
        const IF = this.computeIF(avgPower, ftp);
        return (durationSeconds / 3600) * IF * IF * 100;
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

    // ── PMC projection (pure client-side EMA) ────────────────────────────────

    projectPmc(realData: PmcDataPoint[], dailyTss: number, days: number): PmcDataPoint[] {
        if (!realData.length) return [];
        const last = realData[realData.length - 1];
        const kCTL = 1 - Math.exp(-1 / 42);
        const kATL = 1 - Math.exp(-1 / 7);

        let ctl = last.ctl;
        let atl = last.atl;
        const lastDate = new Date(last.date);
        const result: PmcDataPoint[] = [];

        for (let i = 1; i <= days; i++) {
            ctl = ctl + (dailyTss - ctl) * kCTL;
            atl = atl + (dailyTss - atl) * kATL;
            const d = new Date(lastDate);
            d.setDate(d.getDate() + i);
            result.push({
                date: d.toISOString().split('T')[0],
                ctl: Math.round(ctl * 10) / 10,
                atl: Math.round(atl * 10) / 10,
                tsb: Math.round((ctl - atl) * 10) / 10,
                dailyTss,
                predicted: true,
            });
        }
        return result;
    }

    findPeakForm(data: PmcDataPoint[]): { date: string; tsb: number } | null {
        if (!data.length) return null;
        return data.reduce((best, d) => (d.tsb > best.tsb ? d : best), data[0]);
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
                }));
                resolve(records);
            });
        });
    }
}
