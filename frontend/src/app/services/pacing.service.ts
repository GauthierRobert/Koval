import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {finalize, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';

export interface AthleteProfile {
  ftp?: number;
  weightKg?: number;
  thresholdPaceSec?: number;
  swimCssSec?: number;
  fatigueResistance?: number;
  nutritionPreference?: 'GELS' | 'DRINK' | 'SOLID' | 'MIXED';
  temperature?: number;
  windSpeed?: number;
  targetPowerWatts?: number;
  targetPaceSecPerKm?: number;
  swimDistanceM?: number;
  targetSwimPaceSecPer100m?: number;
  bikeType?: 'TT' | 'ROAD_AERO' | 'ROAD';
}

export interface SegmentRange {
  start: number;
  end: number;
}

export interface PacingSegment {
  startDistance: number;
  endDistance: number;
  discipline: string;
  targetPower?: number;
  targetPace?: string;
  estimatedSpeedKmh?: number;
  estimatedSegmentTime: number;
  cumulativeFatigue: number;
  nutritionSuggestion?: string;
  gradient: number;
  elevation: number;
}

export interface RouteCoordinate {
  lat: number;
  lon: number;
  elevation: number;
  distance: number;
}

export interface PacingSummary {
  totalDistance: number;
  estimatedTotalTime: number;
  averagePower?: number;
  averagePace?: string;
  totalCalories: number;
  nutritionPlan: string;
  targetBasis?: string;
  computedTarget?: number;
}

export interface PacingPlanResponse {
  bikeSegments: PacingSegment[] | null;
  runSegments: PacingSegment[] | null;
  bikeSummary: PacingSummary | null;
  runSummary: PacingSummary | null;
  swimSummary: PacingSummary | null;
  bikeRouteCoordinates: RouteCoordinate[] | null;
  runRouteCoordinates: RouteCoordinate[] | null;
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
    gpxFile: File | null,
    bikeGpxFile: File | null,
    runGpxFile: File | null,
    profile: AthleteProfile,
    discipline: string,
    bikeLoops: number,
    runLoops: number,
  ): Observable<PacingPlanResponse> {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    const formData = new FormData();
    if (gpxFile) formData.append('gpx', gpxFile);
    if (bikeGpxFile) formData.append('bikeGpx', bikeGpxFile);
    if (runGpxFile) formData.append('runGpx', runGpxFile);
    formData.append('profile', JSON.stringify(profile));
    formData.append('discipline', discipline);
    if (bikeLoops > 1) formData.append('bikeLoops', String(bikeLoops));
    if (runLoops > 1) formData.append('runLoops', String(runLoops));

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
