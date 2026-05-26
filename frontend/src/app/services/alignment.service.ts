import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AlignmentEstimate,
  AlignmentHistoryPoint,
} from '../models/alignment.model';

/** Wire shape of a session after an alignment rating is set (subset of CompletedSession). */
export interface AlignmentUpdateResult {
  id: string;
  alignmentScore?: import('../models/alignment.model').AlignmentScore | null;
}

/**
 * Reads and writes per-session alignment ratings: the deterministic estimate, the athlete's
 * self-rating, the coach rating, and the evolution history used by the analytics/coach charts.
 */
@Injectable({ providedIn: 'root' })
export class AlignmentService {
  private readonly apiUrl = `${environment.apiUrl}/api/sessions`;
  private http = inject(HttpClient);

  /** Deterministic suggestion for pre-filling the rating modal. */
  getEstimate(sessionId: string): Observable<AlignmentEstimate> {
    return this.http.get<AlignmentEstimate>(`${this.apiUrl}/${sessionId}/alignment/estimate`);
  }

  /** Set the athlete's own rating (owner only). */
  setAthleteScore(
    sessionId: string,
    score: number,
    note: string | null,
  ): Observable<AlignmentUpdateResult> {
    return this.http.put<AlignmentUpdateResult>(`${this.apiUrl}/${sessionId}/alignment/athlete`, {
      score,
      note,
    });
  }

  /** Set the coach rating, validating or overriding the athlete's (coach only). */
  setCoachScore(
    sessionId: string,
    score: number,
    note: string | null,
  ): Observable<AlignmentUpdateResult> {
    return this.http.put<AlignmentUpdateResult>(`${this.apiUrl}/${sessionId}/alignment/coach`, {
      score,
      note,
    });
  }

  /** Scored sessions over a date range for the evolution chart; pass athleteId (coach) for an athlete. */
  getHistory(from: string, to: string, athleteId?: string): Observable<AlignmentHistoryPoint[]> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (athleteId) params = params.set('athleteId', athleteId);
    return this.http.get<AlignmentHistoryPoint[]>(`${this.apiUrl}/alignment/history`, { params });
  }
}
