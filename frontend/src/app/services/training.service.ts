import {DestroyRef, inject, Injectable} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, combineLatest, Observable, of} from 'rxjs';
import {filter, map, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {AuthService} from './auth.service';

export type SportFilter = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | null;

export const SPORT_OPTIONS: { label: string; value: SportFilter }[] = [
    { label: 'Swim', value: 'SWIMMING' },
    { label: 'Bike', value: 'CYCLING' },
    { label: 'Run', value: 'RUNNING' },
    { label: 'Brick', value: 'BRICK' },
];

export type TrainingType =
    | 'VO2MAX'
    | 'THRESHOLD'
    | 'SWEET_SPOT'
    | 'ENDURANCE'
    | 'SPRINT'
    | 'RECOVERY'
    | 'MIXED'
    | 'TEST';

export type TrainingVisibility = 'PUBLIC' | 'PRIVATE' | 'COACH_ONLY';

export const TRAINING_TYPES: TrainingType[] = [
    'VO2MAX',
    'THRESHOLD',
    'SWEET_SPOT',
    'ENDURANCE',
    'SPRINT',
    'RECOVERY',
    'MIXED',
    'TEST',
];

export const TRAINING_TYPE_COLORS: Record<TrainingType, string> = {
    VO2MAX: '#ef4444',
    THRESHOLD: '#f97316',
    SWEET_SPOT: '#eab308',
    ENDURANCE: '#22c55e',
    SPRINT: '#a855f7',
    RECOVERY: '#06b6d4',
    MIXED: '#6366f1',
    TEST: '#ec4899',
};

export const TRAINING_TYPE_LABELS: Record<TrainingType, string> = {
    VO2MAX: 'VO2max',
    THRESHOLD: 'Threshold',
    SWEET_SPOT: 'Sweet Spot',
    ENDURANCE: 'Endurance',
    SPRINT: 'Sprint',
    RECOVERY: 'Recovery',
    MIXED: 'Mixed',
    TEST: 'Test',
};

export interface WorkoutBlock {
    type: 'WARMUP' | 'STEADY' | 'INTERVAL' | 'COOLDOWN' | 'RAMP' | 'FREE' | 'PAUSE';
    durationSeconds?: number;
    distanceMeters?: number;
    intensityTarget?: number;
    intensityStart?: number;
    intensityEnd?: number;
    cadenceTarget?: number;
    zoneTarget?: string;
    zoneSystemId?: string;
    label: string;
    zoneLabel?: string;
}

export interface Training {
    id: string;
    title: string;
    description: string;
    blocks?: WorkoutBlock[];
    sportType: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
    trainingType?: TrainingType;
    groupIds?: string[];
    visibility?: TrainingVisibility;
    createdBy?: string;
    estimatedTss?: number;
    estimatedIf?: number;
    estimatedDurationSeconds?: number;
    estimatedDistance?: number;
    zoneSystemId?: string;
    createdAt?: string;
    clubId?: string;
    clubGroupIds?: string[];
}

/** Returns true when at least one block is distance-based (no explicit durationSeconds). */
export function hasDurationEstimate(training: Training): boolean {
    return (training.blocks ?? []).some(
        (b) => (b.distanceMeters ?? 0) > 0 && !((b.durationSeconds ?? 0) > 0)
    );
}

@Injectable({
    providedIn: 'root',
})
export class TrainingService {
    private apiUrl = `${environment.apiUrl}/api/trainings`;
    private http = inject(HttpClient);

    private trainingsSubject = new BehaviorSubject<Training[]>([]);
    trainings$ = this.trainingsSubject.asObservable();

    private selectedTrainingSubject = new BehaviorSubject<Training | null>(null);
    selectedTraining$ = this.selectedTrainingSubject.asObservable();

    private errorSubject = new BehaviorSubject<string | null>(null);
    error$ = this.errorSubject.asObservable();

    private clubTrainingsSubject = new BehaviorSubject<Training[]>([]);
    clubTrainings$ = this.clubTrainingsSubject.asObservable();

    // Active context: 'mine' | 'club:{clubId}' | 'group:{groupId}'
    private activeContextSubject = new BehaviorSubject<string>('mine');
    activeContext$ = this.activeContextSubject.asObservable();

    // ── Filter state (shared across sidebar list + filter bar) ──────────
    private tagFilterSubject = new BehaviorSubject<string | null>(null);
    private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
    private typeFilterSubject = new BehaviorSubject<TrainingType | null>(null);

    activeTagFilter$ = this.tagFilterSubject.asObservable();
    activeSportFilter$ = this.sportFilterSubject.asObservable();
    activeTypeFilter$ = this.typeFilterSubject.asObservable();

    availableTags$ = this.trainings$.pipe(
        map((trainings) => {
            const tagSet = new Set<string>();
            trainings.forEach((t) => t.groupIds?.forEach((tag) => tagSet.add(tag)));
            return Array.from(tagSet).sort();
        }),
    );

    filteredTrainings$ = combineLatest([
        this.trainingsSubject,
        this.clubTrainingsSubject,
        this.activeContextSubject,
        this.tagFilterSubject,
        this.sportFilterSubject,
        this.typeFilterSubject,
    ]).pipe(
        map(([mine, club, context, tag, sport, type]) => {
            let result: Training[];
            if (context.startsWith('club:')) {
                const clubId = context.slice(5);
                result = club.filter((t) => t.clubId === clubId);
            } else if (context.startsWith('group:')) {
                const groupId = context.slice(6);
                result = mine.filter((t) => t.groupIds?.includes(groupId));
            } else {
                result = mine;
                if (tag === '__mine__') result = result.filter((t) => !t.groupIds?.length);
                else if (tag) result = result.filter((t) => t.groupIds?.includes(tag));
            }
            if (sport) result = result.filter((t) => t.sportType === sport);
            if (type) result = result.filter((t) => t.trainingType === type);
            return [...result].sort((a, b) => {
                const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
                const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
                return db - da;
            });
        }),
    );

    setTagFilter(value: string): void {
        this.tagFilterSubject.next(this.tagFilterSubject.value === value ? null : value);
    }

    setSportFilter(value: SportFilter): void {
        this.sportFilterSubject.next(this.sportFilterSubject.value === value ? null : value);
    }

    setTypeFilter(value: TrainingType): void {
        this.typeFilterSubject.next(this.typeFilterSubject.value === value ? null : value);
    }

    setContext(context: string): void {
        this.activeContextSubject.next(context);
        if (context.startsWith('club:') && this.clubTrainingsSubject.value.length === 0) {
            this.loadClubTrainings();
        }
        if (!context.startsWith('mine')) {
            this.tagFilterSubject.next(null);
        }
    }

    loadClubTrainings(): void {
        this.http.get<Training[]>(`${this.apiUrl}/club-trainings`).subscribe({
            next: (trainings) => this.clubTrainingsSubject.next(trainings),
            error: () => this.clubTrainingsSubject.next([]),
        });
    }

    private static readonly FTP_STORAGE_KEY = 'koval_ftp';

    private ftpSubject = new BehaviorSubject<number | null>(this.loadFtp());
    ftp$ = this.ftpSubject.asObservable();

    private authService = inject(AuthService);
    private destroyRef = inject(DestroyRef);

    constructor() {
        this.authService.user$.pipe(
            filter(user => !!user),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(() => {
            this.selectedTrainingSubject.next(null);
            this.loadTrainings();
        });
    }

    get currentFtp(): number | null {
        return this.ftpSubject.value;
    }

    private loadFtp(): number | null {
        const stored = localStorage.getItem(TrainingService.FTP_STORAGE_KEY);
        if (stored) {
            const parsed = parseInt(stored, 10);
            if (!isNaN(parsed) && parsed > 0) return parsed;
        }
        return null;
    }

    loadTrainings(): void {
        this.errorSubject.next(null);
        this.http.get<Training[]>(this.apiUrl).subscribe({
            next: (trainings) => {
                this.trainingsSubject.next(trainings);
                if (!this.selectedTrainingSubject.value && trainings?.length > 0) {
                    this.selectedTrainingSubject.next(trainings[0]);
                }
            },
            error: () => {
                this.errorSubject.next('Failed to load trainings');
                this.trainingsSubject.next([]);
            },
        });
    }

    getTrainingById(id: string): Observable<Training> {
        const cached = this.trainingsSubject.value.find((t) => t.id === id);
        if (cached) {
            return of(cached);
        }
        return this.http.get<Training>(`${this.apiUrl}/${id}`);
    }

    createTraining(training: Partial<Training>): Observable<Training> {
        return this.http.post<Training>(this.apiUrl, training).pipe(
            tap(newTraining => {
                const current = this.trainingsSubject.value;
                this.trainingsSubject.next([...current, newTraining]);
            })
        );
    }

    updateTraining(id: string, training: Partial<Training>): Observable<Training> {
        return this.http.put<Training>(`${this.apiUrl}/${id}`, training).pipe(
            tap(updated => {
                const current = this.trainingsSubject.value;
                const index = current.findIndex(t => t.id === id);
                if (index !== -1) {
                    current[index] = updated;
                    this.trainingsSubject.next([...current]);
                }
                if (this.selectedTrainingSubject.value?.id === id) {
                    this.selectedTrainingSubject.next(updated);
                }
            })
        );
    }

    selectTraining(training: Training | null): void {
        this.selectedTrainingSubject.next(training);
    }

    setFtp(ftp: number): void {
        this.ftpSubject.next(ftp);
        localStorage.setItem(TrainingService.FTP_STORAGE_KEY, String(ftp));
    }

    deleteTraining(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
            tap(() => this.removeTrainingLocally(id))
        );
    }

    removeTrainingLocally(id: string): void {
        const current = this.trainingsSubject.value.filter((t) => t.id !== id);
        this.trainingsSubject.next(current);
        if (this.selectedTrainingSubject.value?.id === id) {
            this.selectedTrainingSubject.next(current.length > 0 ? current[0] : null);
        }
    }

    discoverTrainings(): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/discover`);
    }

    searchByType(type: TrainingType): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/search/type`, {
            params: { type },
        });
    }

    searchByGroup(group: string): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/search`, {
            params: { group },
        });
    }

    updateTrainingGroups(trainingId: string, groupIds: string[]): Observable<Training> {
        return this.http.put<Training>(`${this.apiUrl}/${trainingId}`, { groupIds });
    }

    getTrainingFolders(): Observable<Record<string, Training[]>> {
        return this.http.get<Record<string, Training[]>>(`${this.apiUrl}/folders`);
    }
}
