import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {AthleteProfile, PacingPlanResponse} from './pacing.service';

export type DistanceCategory =
  // Triathlon
  | 'TRI_PROMO' | 'TRI_SUPER_SPRINT' | 'TRI_SPRINT' | 'TRI_OLYMPIC'
  | 'TRI_HALF' | 'TRI_IRONMAN' | 'TRI_ULTRA'
  | 'TRI_AQUATHLON' | 'TRI_DUATHLON' | 'TRI_AQUABIKE' | 'TRI_CROSS'
  // Running
  | 'RUN_5K' | 'RUN_10K' | 'RUN_HALF_MARATHON' | 'RUN_MARATHON' | 'RUN_ULTRA'
  // Cycling
  | 'BIKE_GRAN_FONDO' | 'BIKE_MEDIO_FONDO' | 'BIKE_TT' | 'BIKE_ULTRA'
  // Swimming
  | 'SWIM_1500M' | 'SWIM_5K' | 'SWIM_10K' | 'SWIM_MARATHON' | 'SWIM_ULTRA';

export interface Race {
  id: string;
  title: string;
  sport: string;
  location?: string;
  country?: string;
  region?: string;
  distance?: string;
  distanceCategory?: DistanceCategory;
  swimDistanceM?: number;
  bikeDistanceM?: number;
  runDistanceM?: number;
  elevationGainM?: number;
  description?: string;
  website?: string;
  scheduledDate?: string;
  hasSwimGpx?: boolean;
  hasBikeGpx?: boolean;
  hasRunGpx?: boolean;
  swimGpxLoops?: number;
  bikeGpxLoops?: number;
  runGpxLoops?: number;
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

export interface SportFacet {
  sport: string;
  raceCount: number;
  countryCount: number;
}

export interface CountryFacet {
  country: string;
  raceCount: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ── Race briefing pack ─────────────────────────────────────────────────────
export interface RaceBriefing {
  race: BriefingRaceHeader;
  courses: BriefingCourseSummary[];
  targetZones: BriefingZoneTarget[];
  weather: BriefingWeather | null;
  gear: BriefingGearChecklist;
  generatedAt: string;
}

export interface BriefingRaceHeader {
  id: string;
  title: string;
  sport: string;
  category?: DistanceCategory;
  distance?: string;
  location?: string;
  country?: string;
  scheduledDate?: string;
  website?: string;
  description?: string;
}

export interface BriefingCourseSummary {
  discipline: string;
  distanceM: number;
  elevationGainM: number;
  elevationLossM: number;
  maxGradientPercent: number;
  avgGradientPercent: number;
  keySegments: BriefingKeySegment[];
  start: { lat: number; lon: number } | null;
}

export interface BriefingKeySegment {
  startDistanceM: number;
  endDistanceM: number;
  gradientPercent: number;
  elevationGainM: number;
  label: string;
}

export interface BriefingZoneTarget {
  sportType: string;
  systemName: string;
  referenceType?: string;
  referenceName?: string;
  referenceUnit?: string;
  zones: BriefingZoneRow[];
}

export interface BriefingZoneRow {
  label: string;
  low: number;
  high: number;
  description?: string;
}

export interface BriefingWeather {
  latitude: number;
  longitude: number;
  timezone?: string;
  source: string;
  hourly: BriefingHourly[];
  warning?: string;
}

export interface BriefingHourly {
  time: string;
  temperatureC?: number;
  precipitationMm?: number;
  windSpeedKmh?: number;
  weatherCode?: number;
}

export interface BriefingGearChecklist {
  groups: BriefingGearGroup[];
}

export interface BriefingGearGroup {
  name: string;
  items: string[];
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
    return this.http.get<{ content: Race[] }>(this.apiUrl, { params }).pipe(
      map((page) => page.content),
      tap((races) => this.searchResultsSubject.next(races)),
    );
  }

  getRace(id: string): Observable<Race> {
    return this.http.get<Race>(`${this.apiUrl}/${id}`);
  }

  createRace(title: string): Observable<Race> {
    return this.http.post<Race>(this.apiUrl, { title });
  }

  updateRace(id: string, updates: Partial<Race>): Observable<Race> {
    return this.http.put<Race>(`${this.apiUrl}/${id}`, updates);
  }

  aiComplete(raceId: string): Observable<Race> {
    return this.http.post<Race>(`${this.apiUrl}/${raceId}/ai-complete`, {});
  }

  webSearch(query: string): Observable<Race> {
    return this.http.post<Race>(`${this.apiUrl}/web-search`, { query });
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

  getBriefing(raceId: string): Observable<RaceBriefing> {
    return this.http.get<RaceBriefing>(`${this.apiUrl}/${raceId}/briefing`);
  }

  getSportFacets(): Observable<SportFacet[]> {
    return this.http.get<SportFacet[]>(`${this.apiUrl}/facets/sports`);
  }

  getCountryFacets(sport: string): Observable<CountryFacet[]> {
    return this.http.get<CountryFacet[]>(`${this.apiUrl}/facets/countries`, {
      params: { sport },
    });
  }

  browseRaces(sport: string, country: string, page = 0, size = 20): Observable<PageResponse<Race>> {
    return this.http.get<PageResponse<Race>>(`${this.apiUrl}/browse`, {
      params: { sport, country, page: page.toString(), size: size.toString() },
    });
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
