import {Injectable, inject} from '@angular/core';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {map} from 'rxjs/operators';
import {ReceivedTraining, SportFilter, Training, TrainingType} from '../models/training.model';
import {TrainingService} from './training.service';

/**
 * Manages training list filtering and context switching.
 * Contexts: 'mine' for personal trainings, or an originName (e.g. 'BTC') for received trainings.
 */
@Injectable({
    providedIn: 'root',
})
export class TrainingFilterService {
    private trainingService = inject(TrainingService);

    // Active context: 'mine' | originName (e.g. 'BTC', 'T3')
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

    receivedTrainings$ = this.trainingService.receivedTrainings$;

    filteredTrainings$ = combineLatest([
        this.trainingService.trainings$,
        this.trainingService.receivedTrainings$,
        this.activeContextSubject,
        this.tagFilterSubject,
        this.sportFilterSubject,
        this.typeFilterSubject,
    ]).pipe(
        map(([mine, received, context, tag, sport, type]) => {
            let result: Training[];
            if (context === 'mine') {
                result = mine;
                if (tag === '__mine__') result = result.filter((t) => !t.groupIds?.length);
                else if (tag) result = result.filter((t) => t.groupIds?.includes(tag));
            } else {
                result = received
                    .filter((r: ReceivedTraining) => r.originName === context)
                    .map((r: ReceivedTraining) => this.receivedToTraining(r));
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
        if (context !== 'mine') {
            this.trainingService.loadReceivedTrainings();
            this.tagFilterSubject.next(null);
        }
    }

    private receivedToTraining(r: ReceivedTraining): Training {
        return {
            id: r.trainingId,
            title: r.title,
            description: r.description ?? '',
            sportType: r.sportType ?? 'CYCLING',
            trainingType: r.trainingType,
            estimatedTss: r.estimatedTss,
            estimatedIf: r.estimatedIf,
            estimatedDurationSeconds: r.estimatedDurationSeconds,
            createdAt: r.receivedAt,
            _receivedMeta: {
                assignedByName: r.assignedByName,
                origin: r.origin,
                originName: r.originName,
            },
        } as Training;
    }
}
