import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { AuthService } from './auth.service';

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
    type: 'WARMUP' | 'STEADY' | 'INTERVAL' | 'COOLDOWN' | 'RAMP' | 'FREE';
    durationSeconds: number;
    powerTargetPercent?: number;
    powerStartPercent?: number;
    powerEndPercent?: number;
    cadenceTarget?: number;
    repeats?: number;
    label: string;
}

export interface Training {
    id: string;
    title: string;
    description: string;
    blocks: WorkoutBlock[];
    trainingType?: TrainingType;
    tags?: string[];
    visibility?: TrainingVisibility;
    createdBy?: string;
    estimatedTss?: number;
    estimatedIf?: number;
    createdAt?: string;
}

const MOCK_TRAININGS: Training[] = [
    {
        id: '1',
        title: 'FTP Booster - Over-Unders',
        description:
            'A classic workout to increase your lactate threshold. 3 sets of 8 minutes alternating between 95% and 105% of FTP.',
        trainingType: 'THRESHOLD',
        tags: ['ftp', 'lactate-threshold'],
        blocks: [
            { type: 'WARMUP', durationSeconds: 600, powerTargetPercent: 50, label: 'Warm-up' },
            { type: 'STEADY', durationSeconds: 300, powerTargetPercent: 75, label: 'Preparation' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 105, label: 'Over' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 95, label: 'Under' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 105, label: 'Over' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 95, label: 'Under' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 105, label: 'Over' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 95, label: 'Under' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 105, label: 'Over' },
            { type: 'INTERVAL', durationSeconds: 60, powerTargetPercent: 95, label: 'Under' },
            { type: 'COOLDOWN', durationSeconds: 600, powerTargetPercent: 50, label: 'Cool-down' },
        ],
    },
    {
        id: '2',
        title: 'Sprints & Explosiveness',
        description: 'Short, high-intensity bursts to build pure power and neuromuscular coordination.',
        trainingType: 'SPRINT',
        tags: ['sprint', 'neuromuscular'],
        blocks: [
            {
                type: 'WARMUP',
                durationSeconds: 900,
                powerTargetPercent: 45,
                label: 'Progressive Warm-up',
            },
            {
                type: 'INTERVAL',
                durationSeconds: 15,
                powerTargetPercent: 250,
                label: 'All-out Sprint',
            },
            { type: 'STEADY', durationSeconds: 285, powerTargetPercent: 50, label: 'Recovery' },
            {
                type: 'INTERVAL',
                durationSeconds: 15,
                powerTargetPercent: 250,
                label: 'All-out Sprint',
            },
            { type: 'STEADY', durationSeconds: 285, powerTargetPercent: 50, label: 'Recovery' },
            {
                type: 'INTERVAL',
                durationSeconds: 15,
                powerTargetPercent: 250,
                label: 'All-out Sprint',
            },
            { type: 'STEADY', durationSeconds: 285, powerTargetPercent: 50, label: 'Recovery' },
            { type: 'COOLDOWN', durationSeconds: 600, powerTargetPercent: 40, label: 'Cool-down' },
        ],
    },
    {
        id: '3',
        title: 'Endurance with Ramps & Free Ride',
        description:
            'A varied endurance session featuring a progressive warm-up ramp, steady state efforts, and a free ride segment.',
        trainingType: 'ENDURANCE',
        tags: ['endurance', 'base'],
        blocks: [
            {
                type: 'RAMP',
                durationSeconds: 600,
                powerStartPercent: 40,
                powerEndPercent: 70,
                label: 'Ramp Up Warm-up',
            },
            {
                type: 'STEADY',
                durationSeconds: 600,
                powerTargetPercent: 75,
                label: 'Endurance Base',
            },
            {
                type: 'RAMP',
                durationSeconds: 300,
                powerStartPercent: 75,
                powerEndPercent: 95,
                label: 'Threshold Build',
            },
            {
                type: 'INTERVAL',
                durationSeconds: 120,
                powerTargetPercent: 105,
                label: 'Over Threshold',
            },
            {
                type: 'RAMP',
                durationSeconds: 300,
                powerStartPercent: 95,
                powerEndPercent: 75,
                label: 'Threshold Reset',
            },
            { type: 'FREE', durationSeconds: 900, label: 'Free Ride / Integration' },
            {
                type: 'RAMP',
                durationSeconds: 600,
                powerStartPercent: 60,
                powerEndPercent: 40,
                label: 'Cool-down Ramp',
            },
        ],
    },
];

@Injectable({
    providedIn: 'root',
})
export class TrainingService {
    private apiUrl = 'http://localhost:8080/api/trainings';
    private http = inject(HttpClient);
    private authService = inject(AuthService);

    private trainingsSubject = new BehaviorSubject<Training[]>(MOCK_TRAININGS);
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

    private getUserId(): string {
        let userId = 'mock-user-123';
        const sub = this.authService.user$.subscribe((user) => {
            if (user) userId = user.id;
        });
        sub.unsubscribe();
        return userId;
    }

    loadTrainings(): void {
        this.http
            .get<Training[]>(this.apiUrl, {
                headers: { 'X-User-Id': this.getUserId() },
            })
            .subscribe({
                next: (trainings) => {
                    this.trainingsSubject.next(trainings);
                    if (!this.selectedTrainingSubject.value && trainings.length > 0) {
                        this.selectedTrainingSubject.next(trainings[0]);
                    }
                },
                error: () => {
                    this.trainingsSubject.next(MOCK_TRAININGS);
                    this.selectedTrainingSubject.next(MOCK_TRAININGS[0]);
                },
            });
    }

    getTrainingById(id: string): Observable<Training> {
        const cached = this.trainingsSubject.value.find((t) => t.id === id);
        if (cached) {
            return of(cached);
        }
        return this.http.get<Training>(`${this.apiUrl}/${id}`, {
            headers: { 'X-User-Id': this.getUserId() },
        });
    }

    selectTraining(training: Training | null): void {
        this.selectedTrainingSubject.next(training);
    }

    setFtp(ftp: number): void {
        this.ftpSubject.next(ftp);
        localStorage.setItem(TrainingService.FTP_STORAGE_KEY, String(ftp));
    }

    deleteTraining(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`, {
            headers: { 'X-User-Id': this.getUserId() },
        });
    }

    removeTrainingLocally(id: string): void {
        const current = this.trainingsSubject.value.filter((t) => t.id !== id);
        this.trainingsSubject.next(current);
        if (this.selectedTrainingSubject.value?.id === id) {
            this.selectedTrainingSubject.next(current.length > 0 ? current[0] : null);
        }
    }

    discoverTrainings(): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/discover`, {
            headers: { 'X-User-Id': this.getUserId() },
        });
    }

    searchByType(type: TrainingType): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/search/type`, {
            params: { type },
            headers: { 'X-User-Id': this.getUserId() },
        });
    }

    searchByTag(tag: string): Observable<Training[]> {
        return this.http.get<Training[]>(`${this.apiUrl}/search`, {
            params: { tag },
            headers: { 'X-User-Id': this.getUserId() },
        });
    }
}
