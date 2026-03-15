import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {map} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {SportFilter, Training, TrainingType} from '../models/training.model';
import {TrainingService} from './training.service';

/**
 * Manages training list filtering, context switching, and club training loading.
 * Extracted from TrainingService to separate filter/view concerns from CRUD.
 */
@Injectable({
    providedIn: 'root',
})
export class TrainingFilterService {
    private apiUrl = `${environment.apiUrl}/api/trainings`;
    private http = inject(HttpClient);
    private trainingService = inject(TrainingService);

    private clubTrainingsSubject = new BehaviorSubject<Training[]>([]);
    clubTrainings$ = this.clubTrainingsSubject.asObservable();

    private groupTrainingsSubject = new BehaviorSubject<Training[]>([]);

    // Active context: 'mine' | 'club:{clubId}' | 'group:{groupId}'
    private activeContextSubject = new BehaviorSubject<string>('mine');
    activeContext$ = this.activeContextSubject.asObservable();

    // ── Filter state ──────────────────────────────────────────────────────
    private tagFilterSubject = new BehaviorSubject<string | null>(null);
    private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
    private typeFilterSubject = new BehaviorSubject<TrainingType | null>(null);

    activeTagFilter$ = this.tagFilterSubject.asObservable();
    activeSportFilter$ = this.sportFilterSubject.asObservable();
    activeTypeFilter$ = this.typeFilterSubject.asObservable();

    availableTags$ = this.trainingService.trainings$.pipe(
        map((trainings) => {
            const tagSet = new Set<string>();
            trainings.forEach((t) => t.groupIds?.forEach((tag) => tagSet.add(tag)));
            return Array.from(tagSet).sort();
        }),
    );

    filteredTrainings$ = combineLatest([
        this.trainingService.trainings$,
        this.clubTrainingsSubject,
        this.groupTrainingsSubject,
        this.activeContextSubject,
        this.tagFilterSubject,
        this.sportFilterSubject,
        this.typeFilterSubject,
    ]).pipe(
        map(([mine, club, groupDiscover, context, tag, sport, type]) => {
            let result: Training[];
            if (context.startsWith('club:')) {
                const clubId = context.slice(5);
                result = club.filter((t) => t.clubId === clubId);
            } else if (context.startsWith('group:')) {
                const groupId = context.slice(6);
                // Merge personal trainings (coaches) + discovered trainings (athletes)
                const combined = [...mine, ...groupDiscover];
                const seen = new Set<string>();
                result = combined.filter((t) => {
                    if (!t.groupIds?.includes(groupId)) return false;
                    if (seen.has(t.id!)) return false;
                    seen.add(t.id!);
                    return true;
                });
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
        if (context.startsWith('group:') && this.groupTrainingsSubject.value.length === 0) {
            this.loadGroupTrainings();
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

    loadGroupTrainings(): void {
        this.http.get<Training[]>(`${this.apiUrl}/discover`).subscribe({
            next: (trainings) => this.groupTrainingsSubject.next(trainings),
            error: () => this.groupTrainingsSubject.next([]),
        });
    }
}
