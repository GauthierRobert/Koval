import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

export interface VolumeEntry {
  period: string;
  totalTss: number;
  totalDurationSeconds: number;
  totalDistanceMeters: number;
  sportTss: Record<string, number>;
}

/** Duration label for power curve display */
export const DURATION_LABELS: Record<number, string> = {
  5: '5s',
  15: '15s',
  30: '30s',
  60: '1min',
  120: '2min',
  300: '5min',
  600: '10min',
  1200: '20min',
  1800: '30min',
  3600: '1h',
  5400: '1h30',
  7200: '2h',
};

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly apiUrl = `${environment.apiUrl}/api/sessions`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);

  private powerCurveSubject = new BehaviorSubject<Record<number, number>>({});
  powerCurve$ = this.powerCurveSubject.asObservable();

  private volumeSubject = new BehaviorSubject<VolumeEntry[]>([]);
  volume$ = this.volumeSubject.asObservable();

  private personalRecordsSubject = new BehaviorSubject<Record<number, number>>({});
  personalRecords$ = this.personalRecordsSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  loadPowerCurve(from: string, to: string): void {
    this.loadingSubject.next(true);
    const params = new HttpParams().set('from', from).set('to', to);
    this.http.get<Record<number, number>>(`${this.apiUrl}/power-curve`, { params }).subscribe({
      next: (data) =>
        this.ngZone.run(() => {
          this.powerCurveSubject.next(data);
          this.loadingSubject.next(false);
        }),
      error: () => this.ngZone.run(() => this.loadingSubject.next(false)),
    });
  }

  loadVolume(from: string, to: string, groupBy: 'week' | 'month' = 'week'): void {
    const params = new HttpParams().set('from', from).set('to', to).set('groupBy', groupBy);
    this.http.get<VolumeEntry[]>(`${this.apiUrl}/volume`, { params }).subscribe({
      next: (data) => this.ngZone.run(() => this.volumeSubject.next(data)),
      error: () => {},
    });
  }

  loadPersonalRecords(): void {
    this.http.get<Record<number, number>>(`${this.apiUrl}/personal-records`).subscribe({
      next: (data) => this.ngZone.run(() => this.personalRecordsSubject.next(data)),
      error: () => {},
    });
  }
}
