import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type WorkoutFamily =
  | 'RECOVERY'
  | 'ENDURANCE'
  | 'TEMPO'
  | 'SWEET_SPOT'
  | 'THRESHOLD'
  | 'VO2MAX'
  | 'SPRINT'
  | 'MIXED';

export interface FamilyEffectiveness {
  family: WorkoutFamily;
  sessionCount: number;
  totalTss: number;
  tssShare: number;
  totalDurationSeconds: number;
  avgIntensityFactor: number | null;
  avgAlignment: number | null;
  avgRpe: number | null;
  estimatedWattsPer1000Tss: number | null;
  rank: number;
}

export interface TrainingEffectivenessReport {
  athleteId: string;
  from: string;
  to: string;
  splitDate: string;
  sessionCount: number;
  totalTss: number;
  firstHalfCurve: Record<number, number>;
  secondHalfCurve: Record<number, number>;
  curveGains: Record<number, number>;
  families: FamilyEffectiveness[];
  summary: string;
}

@Injectable({ providedIn: 'root' })
export class TrainingEffectivenessService {
  private readonly apiUrl = `${environment.apiUrl}/api/training/effectiveness`;
  private http = inject(HttpClient);

  evaluate(
    athleteId?: string,
    from?: string,
    to?: string,
  ): Observable<TrainingEffectivenessReport> {
    let params = new HttpParams();
    if (athleteId) params = params.set('athleteId', athleteId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<TrainingEffectivenessReport>(this.apiUrl, { params });
  }
}
