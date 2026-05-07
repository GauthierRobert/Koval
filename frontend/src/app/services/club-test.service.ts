import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ErrorToastService } from './error-toast.service';

export type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';

export type SegmentResultUnit = 'SECONDS' | 'WATTS' | 'PACE_S_PER_KM' | 'PACE_S_PER_100M' | 'METERS';

export type ReferenceTarget =
  | 'FTP'
  | 'CRITICAL_SWIM_SPEED'
  | 'FUNCTIONAL_THRESHOLD_PACE'
  | 'PACE_5K'
  | 'PACE_10K'
  | 'PACE_HALF_MARATHON'
  | 'PACE_MARATHON'
  | 'VO2MAX_POWER'
  | 'VO2MAX_PACE'
  | 'POWER_3MIN'
  | 'POWER_12MIN'
  | 'WEIGHT_KG'
  | 'CUSTOM';

export type RankingMetric = 'TIME_OF_SEGMENT' | 'SUM_OF_TIMES' | 'COMPUTED_REFERENCE';
export type RankingDirection = 'ASC' | 'DESC';
export type IterationStatus = 'OPEN' | 'CLOSED';

export interface TestSegment {
  id: string;
  order: number;
  label: string;
  sportType: SportType;
  distanceMeters?: number | null;
  durationSeconds?: number | null;
  resultUnit: SegmentResultUnit;
  notes?: string | null;
}

export interface ReferenceUpdateRule {
  id: string;
  target: ReferenceTarget;
  customKey?: string | null;
  label: string;
  unit: string;
  formulaExpression: string;
  autoApply: boolean;
}

export interface ClubTestSummary {
  id: string;
  clubId: string;
  name: string;
  description?: string | null;
  competitionMode: boolean;
  archived: boolean;
  segmentCount: number;
  ruleCount: number;
  iterationCount: number;
  currentIterationId?: string | null;
  currentIterationLabel?: string | null;
  createdAt: string;
}

export interface ClubTestDetail {
  id: string;
  clubId: string;
  name: string;
  description?: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  competitionMode: boolean;
  rankingMetric?: RankingMetric | null;
  rankingTarget?: string | null;
  rankingDirection?: RankingDirection | null;
  segments: TestSegment[];
  referenceUpdates: ReferenceUpdateRule[];
  currentIterationId?: string | null;
  archived: boolean;
  hasResults: boolean;
}

export interface ClubTestIteration {
  id: string;
  testId: string;
  clubId: string;
  label: string;
  startDate?: string | null;
  endDate?: string | null;
  status: IterationStatus;
  createdAt: string;
  closedAt?: string | null;
  segments: TestSegment[];
  referenceUpdates: ReferenceUpdateRule[];
  resultCount: number;
}

export interface SegmentResultValue {
  value: number;
  unit: SegmentResultUnit;
  completedSessionId?: string | null;
}

export interface AppliedReferenceUpdate {
  ruleId: string;
  target: ReferenceTarget;
  customKey?: string | null;
  previousValue?: number | null;
  newValue?: number | null;
  appliedAt: string;
  appliedBy: string;
}

export interface ClubTestResult {
  id: string;
  iterationId: string;
  testId: string;
  clubId: string;
  athleteId: string;
  athleteDisplayName?: string | null;
  athleteProfilePicture?: string | null;
  segmentResults: Record<string, SegmentResultValue>;
  computedReferences: Record<string, number>;
  appliedUpdates: AppliedReferenceUpdate[];
  rank?: number | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  recordedBy: string;
}

export interface TestPreset {
  id: string;
  labelKey: string;
  descriptionKey: string;
  segments: TestSegment[];
  referenceUpdates: ReferenceUpdateRule[];
}

export interface CreateClubTestRequest {
  name: string;
  description?: string | null;
  competitionMode: boolean;
  rankingMetric?: RankingMetric | null;
  rankingTarget?: string | null;
  rankingDirection?: RankingDirection | null;
  segments?: TestSegment[];
  referenceUpdates?: ReferenceUpdateRule[];
  presetId?: string | null;
}

export interface UpdateClubTestRequest {
  name?: string;
  description?: string | null;
  competitionMode?: boolean;
  rankingMetric?: RankingMetric | null;
  rankingTarget?: string | null;
  rankingDirection?: RankingDirection | null;
  segments?: TestSegment[];
  referenceUpdates?: ReferenceUpdateRule[];
}

export interface CreateIterationRequest {
  label: string;
  startDate?: string | null;
  endDate?: string | null;
  closeCurrent: boolean;
}

export interface RecordResultRequest {
  athleteId?: string | null;
  segmentResults: Record<string, SegmentResultValue>;
  notes?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ClubTestService {
  private readonly http = inject(HttpClient);
  private readonly ngZone = inject(NgZone);
  private readonly errorToast = inject(ErrorToastService);

  private readonly testsSubject = new BehaviorSubject<ClubTestSummary[]>([]);
  readonly tests$ = this.testsSubject.asObservable();

  private readonly selectedTestSubject = new BehaviorSubject<ClubTestDetail | null>(null);
  readonly selectedTest$ = this.selectedTestSubject.asObservable();

  private readonly iterationsSubject = new BehaviorSubject<ClubTestIteration[]>([]);
  readonly iterations$ = this.iterationsSubject.asObservable();

  private readonly selectedIterationIdSubject = new BehaviorSubject<string | null>(null);
  readonly selectedIterationId$ = this.selectedIterationIdSubject.asObservable();

  private readonly resultsSubject = new BehaviorSubject<ClubTestResult[]>([]);
  readonly results$ = this.resultsSubject.asObservable();

  private readonly myHistorySubject = new BehaviorSubject<ClubTestResult[]>([]);
  readonly myHistory$ = this.myHistorySubject.asObservable();

  private readonly presetsSubject = new BehaviorSubject<TestPreset[]>([]);
  readonly presets$ = this.presetsSubject.asObservable();

  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  readonly loading$ = this.loadingSubject.asObservable();

  resetDetail(): void {
    this.selectedTestSubject.next(null);
    this.iterationsSubject.next([]);
    this.selectedIterationIdSubject.next(null);
    this.resultsSubject.next([]);
    this.myHistorySubject.next([]);
  }

  loadTests(clubId: string, includeArchived = false): void {
    this.loadingSubject.next(true);
    this.http
      .get<ClubTestSummary[]>(`${this.baseUrl(clubId)}`, {
        params: { includeArchived: String(includeArchived) },
      })
      .pipe(catchError(this.handleError([] as ClubTestSummary[])))
      .subscribe((tests) =>
        this.ngZone.run(() => {
          this.testsSubject.next(tests);
          this.loadingSubject.next(false);
        }),
      );
  }

  loadTestDetail(clubId: string, testId: string): void {
    this.http
      .get<ClubTestDetail>(`${this.baseUrl(clubId)}/${testId}`)
      .pipe(catchError(this.handleError(null as ClubTestDetail | null)))
      .subscribe((test) => this.ngZone.run(() => this.selectedTestSubject.next(test)));
  }

  loadIterations(clubId: string, testId: string): void {
    this.http
      .get<ClubTestIteration[]>(`${this.baseUrl(clubId)}/${testId}/iterations`)
      .pipe(catchError(this.handleError([] as ClubTestIteration[])))
      .subscribe((its) =>
        this.ngZone.run(() => {
          this.iterationsSubject.next(its);
          // Default-select the first OPEN iteration, else the most recent.
          const open = its.find((i) => i.status === 'OPEN');
          const target = open?.id ?? its[0]?.id ?? null;
          this.selectedIterationIdSubject.next(target);
          if (target) this.loadResults(clubId, testId, target);
        }),
      );
  }

  selectIteration(clubId: string, testId: string, iterationId: string | null): void {
    this.selectedIterationIdSubject.next(iterationId);
    if (iterationId) this.loadResults(clubId, testId, iterationId);
    else this.resultsSubject.next([]);
  }

  loadResults(clubId: string, testId: string, iterationId: string): void {
    this.http
      .get<ClubTestResult[]>(
        `${this.baseUrl(clubId)}/${testId}/iterations/${iterationId}/results`,
      )
      .pipe(catchError(this.handleError([] as ClubTestResult[])))
      .subscribe((results) => this.ngZone.run(() => this.resultsSubject.next(results)));
  }

  loadMyHistory(clubId: string, testId: string): void {
    this.http
      .get<ClubTestResult[]>(`${this.baseUrl(clubId)}/${testId}/me/history`)
      .pipe(catchError(this.handleError([] as ClubTestResult[])))
      .subscribe((results) => this.ngZone.run(() => this.myHistorySubject.next(results)));
  }

  loadPresets(clubId: string): void {
    if (this.presetsSubject.value.length > 0) return;
    this.http
      .get<TestPreset[]>(`${this.baseUrl(clubId)}/presets`)
      .pipe(catchError(this.handleError([] as TestPreset[])))
      .subscribe((presets) => this.ngZone.run(() => this.presetsSubject.next(presets)));
  }

  createTest(clubId: string, req: CreateClubTestRequest): Observable<ClubTestDetail> {
    return this.http
      .post<ClubTestDetail>(`${this.baseUrl(clubId)}`, req)
      .pipe(tap(() => this.loadTests(clubId)));
  }

  updateTest(clubId: string, testId: string, req: UpdateClubTestRequest): Observable<ClubTestDetail> {
    return this.http
      .put<ClubTestDetail>(`${this.baseUrl(clubId)}/${testId}`, req)
      .pipe(
        tap((updated) => {
          this.selectedTestSubject.next(updated);
          this.loadTests(clubId);
        }),
      );
  }

  archiveTest(clubId: string, testId: string): Observable<ClubTestDetail> {
    return this.http
      .post<ClubTestDetail>(`${this.baseUrl(clubId)}/${testId}/archive`, {})
      .pipe(tap(() => this.loadTests(clubId)));
  }

  startIteration(
    clubId: string,
    testId: string,
    req: CreateIterationRequest,
  ): Observable<ClubTestIteration> {
    return this.http
      .post<ClubTestIteration>(`${this.baseUrl(clubId)}/${testId}/iterations`, req)
      .pipe(tap(() => this.loadIterations(clubId, testId)));
  }

  closeIteration(clubId: string, testId: string, iterationId: string): Observable<ClubTestIteration> {
    return this.http
      .post<ClubTestIteration>(
        `${this.baseUrl(clubId)}/${testId}/iterations/${iterationId}/close`,
        {},
      )
      .pipe(tap(() => this.loadIterations(clubId, testId)));
  }

  recordResult(
    clubId: string,
    testId: string,
    iterationId: string,
    req: RecordResultRequest,
  ): Observable<ClubTestResult> {
    return this.http
      .post<ClubTestResult>(
        `${this.baseUrl(clubId)}/${testId}/iterations/${iterationId}/results`,
        req,
      )
      .pipe(tap(() => this.loadResults(clubId, testId, iterationId)));
  }

  applyReferences(
    clubId: string,
    testId: string,
    iterationId: string,
    resultId: string,
    ruleIds?: string[] | null,
  ): Observable<ClubTestResult> {
    const body = ruleIds && ruleIds.length > 0 ? { ruleIds } : {};
    return this.http
      .post<ClubTestResult>(
        `${this.baseUrl(clubId)}/${testId}/iterations/${iterationId}/results/${resultId}/apply-references`,
        body,
      )
      .pipe(tap(() => this.loadResults(clubId, testId, iterationId)));
  }

  private baseUrl(clubId: string): string {
    return `${environment.apiUrl}/api/clubs/${clubId}/tests`;
  }

  private handleError<T>(fallback: T) {
    return (err: unknown) => {
      const message = this.extractErrorMessage(err);
      if (message) this.errorToast.show(message, 'error');
      return of(fallback);
    };
  }

  private extractErrorMessage(err: unknown): string | null {
    if (!err) return null;
    if (typeof err === 'string') return err;
    const anyErr = err as { error?: { message?: string }; message?: string };
    return anyErr?.error?.message ?? anyErr?.message ?? null;
  }
}
