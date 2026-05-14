import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, combineLatest, forkJoin, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {SportFilter, Training, TrainingType} from '../models/training.model';
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
    // NB: `tagFilterSubject` filters by coach `groupIds` (legacy name predating
    // user tags). User-defined tags use `userTagsSubject` below.
    private tagFilterSubject = new BehaviorSubject<string | null>('__mine__');
    private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
    private typeFilterSubject = new BehaviorSubject<TrainingType | null>(null);
    private userTagsSubject = new BehaviorSubject<Set<string>>(new Set());
    private favoritesOnlySubject = new BehaviorSubject<boolean>(false);

    activeTagFilter$ = this.tagFilterSubject.asObservable();
    activeSportFilter$ = this.sportFilterSubject.asObservable();
    activeTypeFilter$ = this.typeFilterSubject.asObservable();
    activeUserTags$ = this.userTagsSubject.asObservable();
    favoritesOnly$ = this.favoritesOnlySubject.asObservable();

    availableTags$ = this.trainingService.trainings$.pipe(
        map((trainings) => {
            const tagSet = new Set<string>();
            trainings.forEach((t) => t.groupIds?.forEach((tag) => tagSet.add(tag)));
            return Array.from(tagSet).sort();
        }),
    );

    /**
     * User-defined tags across all of the current user's trainings, with usage
     * counts so the UI can surface the most-used ones first.
     */
    availableUserTags$ = this.trainingService.trainings$.pipe(
        map((trainings) => {
            const counts = new Map<string, number>();
            trainings.forEach((t) =>
                t.tags?.forEach((tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1)),
            );
            return Array.from(counts.entries())
                .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
                .map(([tag, count]) => ({ tag, count }));
        }),
    );

    favoritesCount$ = this.trainingService.trainings$.pipe(
        map((trainings) => trainings.filter((t) => t.favorite).length),
    );

    receivedTrainings$ = this.trainingService.receivedTrainings$;

    filteredTrainings$ = combineLatest([
        this.trainingService.trainings$,
        this.trainingService.receivedTrainings$,
        this.activeContextSubject,
        this.tagFilterSubject,
        this.sportFilterSubject,
        this.typeFilterSubject,
        this.userTagsSubject,
        this.favoritesOnlySubject,
    ]).pipe(
        switchMap(([mine, received, context, tag, sport, type, userTags, favoritesOnly]) => {
            if (context === 'mine') {
                let result = mine;
                if (tag === '__mine__') result = result.filter((t) => !t.groupIds?.length);
                else if (tag) result = result.filter((t) => t.groupIds?.includes(tag));
                if (sport) result = result.filter((t) => t.sportType === sport);
                if (type) result = result.filter((t) => t.trainingType === type);
                if (favoritesOnly) result = result.filter((t) => t.favorite);
                if (userTags.size > 0) {
                    result = result.filter((t) =>
                        Array.from(userTags).every((tag) => t.tags?.includes(tag)),
                    );
                }
                return of(this.sortFavoritesFirst(result));
            }

            const filtered = received.filter((r) => r.originName === context);
            if (filtered.length === 0) return of([]);

            return forkJoin(
                filtered.map((r) =>
                    this.trainingService.getTrainingById(r.trainingId).pipe(
                        map(
                            (t) =>
                                ({
                                    ...t,
                                    _receivedMeta: {
                                        assignedByName: r.assignedByName,
                                        origin: r.origin,
                                        originName: r.originName,
                                    },
                                }) as Training,
                        ),
                        catchError(() => of(null)),
                    ),
                ),
            ).pipe(
                map((trainings) => {
                    let result = trainings.filter((t): t is Training => t !== null);
                    if (sport) result = result.filter((t) => t.sportType === sport);
                    if (type) result = result.filter((t) => t.trainingType === type);
                    return this.sortFavoritesFirst(result);
                }),
            );
        }),
    );

    /** Count of trainings after all filters are applied — drives the "Résultats · N" header. */
    filteredCount$ = this.filteredTrainings$.pipe(map((list) => list.length));

    /**
     * Default sort: favorites first, then most-recently-created.
     * Favorites are always pinned to the top — even when the favorites-only
     * filter is off, your starred workouts stay one glance away.
     */
    private sortFavoritesFirst(trainings: Training[]): Training[] {
        return [...trainings].sort((a, b) => {
            const fa = a.favorite ? 1 : 0;
            const fb = b.favorite ? 1 : 0;
            if (fa !== fb) return fb - fa;
            const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
            const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
            return db - da;
        });
    }

    setTagFilter(value: string): void {
        this.tagFilterSubject.next(this.tagFilterSubject.value === value ? null : value);
    }

    setSportFilter(value: SportFilter): void {
        this.sportFilterSubject.next(this.sportFilterSubject.value === value ? null : value);
    }

    setTypeFilter(value: TrainingType): void {
        this.typeFilterSubject.next(this.typeFilterSubject.value === value ? null : value);
    }

    /** Toggle a single user-defined tag in the active filter set. */
    toggleUserTag(tag: string): void {
        const next = new Set(this.userTagsSubject.value);
        if (next.has(tag)) next.delete(tag);
        else next.add(tag);
        this.userTagsSubject.next(next);
    }

    clearUserTags(): void {
        this.userTagsSubject.next(new Set());
    }

    setFavoritesOnly(value: boolean): void {
        this.favoritesOnlySubject.next(value);
    }

    toggleFavoritesOnly(): void {
        this.favoritesOnlySubject.next(!this.favoritesOnlySubject.value);
    }

    setContext(context: string): void {
        this.activeContextSubject.next(context);
        if (context === 'mine') {
            this.tagFilterSubject.next('__mine__');
        } else {
            this.trainingService.loadReceivedTrainings();
            this.tagFilterSubject.next(null);
        }
    }
}
