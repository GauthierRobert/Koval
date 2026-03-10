import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap, finalize } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface AthleteProfile {
  ftp?: number;
  weightKg?: number;
  thresholdPaceSec?: number;
  swimCssSec?: number;
  fatigueResistance?: number;
  nutritionPreference?: 'GELS' | 'DRINK' | 'SOLID' | 'MIXED';
  temperature?: number;
  windSpeed?: number;
}

export interface PacingSegment {
  startDistance: number;
  endDistance: number;
  discipline: string;
  targetPower?: number;
  targetPace?: string;
  estimatedSegmentTime: number;
  cumulativeFatigue: number;
  nutritionSuggestion?: string;
  gradient: number;
  elevation: number;
}

export interface PacingSummary {
  totalDistance: number;
  estimatedTotalTime: number;
  averagePower?: number;
  averagePace?: string;
  totalCalories: number;
  nutritionPlan: string;
}

export interface PacingPlanResponse {
  bikeSegments: PacingSegment[] | null;
  runSegments: PacingSegment[] | null;
  bikeSummary: PacingSummary | null;
  runSummary: PacingSummary | null;
}

@Injectable({ providedIn: 'root' })
export class PacingService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/pacing`;

  private pacingPlanSubject = new BehaviorSubject<PacingPlanResponse | null>(null);
  pacingPlan$ = this.pacingPlanSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  private errorSubject = new BehaviorSubject<string | null>(null);
  error$ = this.errorSubject.asObservable();

  generatePacingPlan(
    gpxFile: File,
    profile: AthleteProfile,
    discipline: string,
  ): Observable<PacingPlanResponse> {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    const formData = new FormData();
    formData.append('gpx', gpxFile);
    formData.append('profile', JSON.stringify(profile));
    formData.append('discipline', discipline);

    return this.http.post<PacingPlanResponse>(`${this.apiUrl}/generate`, formData).pipe(
      tap((plan) => this.pacingPlanSubject.next(plan)),
      finalize(() => this.loadingSubject.next(false)),
    );
  }

  getDefaults(): Observable<AthleteProfile> {
    return this.http.get<AthleteProfile>(`${this.apiUrl}/defaults`);
  }

  clearPlan(): void {
    this.pacingPlanSubject.next(null);
    this.errorSubject.next(null);
  }
}
