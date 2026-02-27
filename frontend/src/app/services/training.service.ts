import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {tap} from 'rxjs/operators';

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
    tags?: string[];
    visibility?: TrainingVisibility;
    createdBy?: string;
    estimatedTss?: number;
    estimatedIf?: number;
    estimatedDurationSeconds?: number;
    estimatedDistance?: number;
    zoneSystemId?: string;
    createdAt?: string;
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
    private apiUrl = 'http://localhost:8080/api/trainings';
    private http = inject(HttpClient);

    private trainingsSubject = new BehaviorSubject<Training[]>([]);
    trainings$ = this.trainingsSubject.asObservable();

    private selectedTrainingSubject = new BehaviorSubject<Training | null>(null);
    selectedTraining$ = this.selectedTrainingSubject.asObservable();

    private static readonly FTP_STORAGE_KEY = 'koval_ftp';

    private ftpSubject = new BehaviorSubject<number>(this.loadFtp());
    ftp$ = this.ftpSubject.asObservable();

    constructor() {
        this.loadTrainings();
    }

    private loadFtp(): number {
        const stored = localStorage.getItem(TrainingService.FTP_STORAGE_KEY);
        if (stored) {
            const parsed = parseInt(stored, 10);
            if (!isNaN(parsed) && parsed > 0) return parsed;
        }
        return 250;
    }

    loadTrainings(): void {
        this.http.get<Training[]>(this.apiUrl).subscribe({
            next: (trainings) => {
                this.trainingsSubject.next(trainings);
                if (!this.selectedTrainingSubject.value && trainings?.length > 0) {
                    this.selectedTrainingSubject.next(trainings[0]);
                }
            },
            error: () => {
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

    searchByTag(tag: string): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/search`, {
            params: { tag },
        });
    }

    updateTrainingTags(trainingId: string, tagIds: string[]): Observable<Training> {
        return this.http.put<Training>(`${this.apiUrl}/${trainingId}`, { tags: tagIds });
    }

    getTrainingFolders(): Observable<Record<string, Training[]>> {
        return this.http.get<Record<string, Training[]>>(`${this.apiUrl}/folders`);
    }
}
