import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AthleteProfile, PacingPlanResponse } from './pacing.service';

export interface Race {
  id: string;
  title: string;
  sport: string;
  location?: string;
  country?: string;
  region?: string;
  distance?: string;
  swimDistanceM?: number;
  bikeDistanceM?: number;
  runDistanceM?: number;
  elevationGainM?: number;
  description?: string;
  website?: string;
  typicalMonth?: number;
  hasSwimGpx?: boolean;
  hasBikeGpx?: boolean;
  hasRunGpx?: boolean;
  createdBy?: string;
  verified?: boolean;
}

export interface SimulationRequest {
  id?: string;
  userId?: string;
  raceId: string;
  goalId?: string;
  discipline: string;
  athleteProfile: AthleteProfile;
  bikeLoops: number;
  runLoops: number;
  label?: string;
  createdAt?: string;
}

export interface RouteCoordinate {
  lat: number;
  lon: number;
  elevation: number;
  distance: number;
}

@Injectable({ providedIn: 'root' })
export class RaceService {
  private http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/races`;
  private readonly pacingUrl = `${environment.apiUrl}/api/pacing`;

  private searchResultsSubject = new BehaviorSubject<Race[]>([]);
  searchResults$ = this.searchResultsSubject.asObservable();

  private simulationRequestsSubject = new BehaviorSubject<SimulationRequest[]>([]);
  simulationRequests$ = this.simulationRequestsSubject.asObservable();

  searchRaces(query?: string, sport?: string, region?: string): Observable<Race[]> {
    const params: Record<string, string> = {};
    if (query) params['q'] = query;
    if (sport) params['sport'] = sport;
    if (region) params['region'] = region;
    return this.http.get<Race[]>(this.apiUrl, { params }).pipe(
      tap((races) => this.searchResultsSubject.next(races)),
    );
  }

  getRace(id: string): Observable<Race> {
    return this.http.get<Race>(`${this.apiUrl}/${id}`);
  }

  createRace(title: string): Observable<Race> {
    return this.http.post<Race>(this.apiUrl, { title });
  }

  aiComplete(raceId: string): Observable<Race> {
    return this.http.post<Race>(`${this.apiUrl}/${raceId}/ai-complete`, {});
  }

  uploadGpx(raceId: string, discipline: string, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`${this.apiUrl}/${raceId}/gpx/${discipline}`, formData);
  }

  deleteGpx(raceId: string, discipline: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${raceId}/gpx/${discipline}`);
  }

  getRouteCoordinates(raceId: string, discipline: string): Observable<RouteCoordinate[]> {
    return this.http.get<RouteCoordinate[]>(`${this.apiUrl}/${raceId}/route/${discipline}`);
  }

  // Simulation requests
  loadSimulationRequests(goalId?: string): void {
    const params: Record<string, string> = {};
    if (goalId) params['goalId'] = goalId;
    this.http.get<SimulationRequest[]>(`${this.pacingUrl}/simulation-requests`, { params }).subscribe({
      next: (reqs) => this.simulationRequestsSubject.next(reqs),
      error: () => {},
    });
  }

  saveSimulationRequest(req: SimulationRequest): Observable<SimulationRequest> {
    return this.http.post<SimulationRequest>(`${this.pacingUrl}/simulation-requests`, req).pipe(
      tap((saved) => {
        this.simulationRequestsSubject.next([saved, ...this.simulationRequestsSubject.value]);
      }),
    );
  }

  deleteSimulationRequest(id: string): Observable<void> {
    return this.http.delete<void>(`${this.pacingUrl}/simulation-requests/${id}`).pipe(
      tap(() => {
        this.simulationRequestsSubject.next(this.simulationRequestsSubject.value.filter((r) => r.id !== id));
      }),
    );
  }

  generateFromRace(
    raceId: string,
    profile: AthleteProfile,
    discipline: string,
    bikeLoops: number,
    runLoops: number,
  ): Observable<PacingPlanResponse> {
    return this.http.post<PacingPlanResponse>(`${this.pacingUrl}/generate-from-race`, {
      raceId,
      profile,
      discipline,
      bikeLoops,
      runLoops,
    });
  }
}
