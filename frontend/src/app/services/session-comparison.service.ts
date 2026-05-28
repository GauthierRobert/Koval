import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Sport-filtered candidate ranked by similarity to the seed session. */
export interface SimilarSessionDto {
  id: string;
  title: string;
  sportType: string;
  completedAt: string;
  totalDurationSeconds: number;
  tss: number | null;
  intensityFactor: number | null;
  normalizedPower: number | null;
  avgPower: number | null;
  totalDistance: number | null;
  similarityPercent: number;
}

export interface ComparisonBlockCell {
  sessionId: string;
  present: boolean;
  durationSeconds: number;
  targetPower: number;
  actualPower: number;
  actualHR: number;
  actualCadence: number;
}

export interface ComparisonAlignedBlock {
  label: string;
  type: string;
  perSession: ComparisonBlockCell[];
}

export interface ComparisonBlockSummary {
  label: string;
  type: string;
  durationSeconds: number;
  targetPower: number;
  actualPower: number;
  actualCadence: number;
  actualHR: number;
  distanceMeters: number | null;
}

export interface ComparisonSessionEntry {
  id: string;
  title: string;
  completedAt: string;
  totalDurationSeconds: number;
  tss: number | null;
  intensityFactor: number | null;
  normalizedPower: number | null;
  avgPower: number | null;
  avgHR: number | null;
  avgCadence: number | null;
  avgSpeed: number | null;
  totalDistance: number | null;
  rpe: number | null;
  blockSummaries: ComparisonBlockSummary[];
  powerCurve: Record<string, number>;
}

export interface ComparisonMetricDelta {
  sessionId: string;
  metric: string;
  referenceValue: number;
  sessionValue: number;
  delta: number;
  reason: string;
}

export interface ComparisonReport {
  sportType: string;
  sessions: ComparisonSessionEntry[];
  alignedBlocks: ComparisonAlignedBlock[];
  biggestDeltas: ComparisonMetricDelta[];
}

@Injectable({ providedIn: 'root' })
export class SessionComparisonService {
  private readonly apiUrl = `${environment.apiUrl}/api/sessions`;
  private http = inject(HttpClient);

  findSimilar(sessionId: string, limit = 10): Observable<SimilarSessionDto[]> {
    return this.http.get<SimilarSessionDto[]>(`${this.apiUrl}/${sessionId}/similar`, {
      params: new HttpParams().set('limit', String(limit)),
    });
  }

  compare(sessionIds: string[]): Observable<ComparisonReport> {
    return this.http.post<ComparisonReport>(`${this.apiUrl}/compare`, { sessionIds });
  }
}
