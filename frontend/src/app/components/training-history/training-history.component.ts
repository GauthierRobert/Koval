import { Component, inject, OnInit } from '@angular/core';

import { CommonModule } from '@angular/common';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import {
    TrainingService,
    Training,
    TrainingType,
    TRAINING_TYPES,
    TRAINING_TYPE_COLORS,
    TRAINING_TYPE_LABELS,
} from '../../services/training.service';
import { AuthService } from '../../services/auth.service';
import { HistoryService } from '../../services/history.service';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map, filter } from 'rxjs/operators';

type SportFilter = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | null;

const SPORT_OPTIONS: { label: string; value: SportFilter }[] = [
    { label: 'Swim', value: 'SWIMMING' },
    { label: 'Bike', value: 'CYCLING' },
    { label: 'Run', value: 'RUNNING' },
    { label: 'Brick', value: 'BRICK' },
];

@Component({
    selector: 'app-training-history',
    standalone: true,
    imports: [CommonModule, SportIconComponent],
    templateUrl: './training-history.component.html',
    styleUrl: './training-history.component.css',
})
export class TrainingHistoryComponent implements OnInit {
    private trainingService = inject(TrainingService);
    private historyService = inject(HistoryService);
    private authService = inject(AuthService);

    readonly sportOptions = SPORT_OPTIONS;
    readonly trainingTypes = TRAINING_TYPES;

    selectedTraining$ = this.trainingService.selectedTraining$;

    // Tag folders
    folders: Record<string, Training[]> = {};
    folderNames: string[] = [];
    expandedFolder: string | null = null;

    // ── Filters ─────────────────────────────────────────────────────────
    // null = no filter; '__mine__' = My Workouts (no tags); string = tag name
    private tagFilterSubject = new BehaviorSubject<string | null>(null);
    private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
    private typeFilterSubject = new BehaviorSubject<TrainingType | null>(null);

    activeTagFilter$ = this.tagFilterSubject.asObservable();
    activeSportFilter$ = this.sportFilterSubject.asObservable();
    activeTypeFilter$ = this.typeFilterSubject.asObservable();

    /** Unique tags collected from all loaded trainings. */
    availableTags$: Observable<string[]> = this.trainingService.trainings$.pipe(
        map((trainings) => {
            const tagSet = new Set<string>();
            trainings.forEach((t) => t.tags?.forEach((tag) => tagSet.add(tag)));
            return Array.from(tagSet).sort();
        }),
    );

    filteredTrainings$: Observable<Training[]> = combineLatest([
        this.trainingService.trainings$,
        this.tagFilterSubject,
        this.sportFilterSubject,
        this.typeFilterSubject,
    ]).pipe(
        map(([trainings, tag, sport, type]) => {
            let result = trainings;
            if (tag === '__mine__') result = result.filter((t) => !t.tags?.length);
            else if (tag) result = result.filter((t) => t.tags?.includes(tag));
            if (sport) result = result.filter((t) => t.sportType === sport);
            if (type) result = result.filter((t) => t.trainingType === type);
            return result;
        }),
    );

    ngOnInit(): void {
        this.authService.user$.pipe(
            filter((u) => !!u && !!u.tags?.length),
        ).subscribe(() => this.loadFolders());
    }

    private loadFolders(): void {
        this.trainingService.getTrainingFolders().subscribe({
            next: (data) => {
                this.folders = data;
                this.folderNames = Object.keys(data).sort();
            },
            error: () => {
                this.folders = {};
                this.folderNames = [];
            },
        });
    }

    expandFolder(name: string): void {
        this.expandedFolder = name;
    }

    collapseFolder(): void {
        this.expandedFolder = null;
    }

    onSelectFolderTraining(training: Training): void {
        this.historyService.selectSession(null);
        this.trainingService.selectTraining(training);
    }

    onSelect(training: Training): void {
        this.historyService.selectSession(null);
        this.trainingService.selectTraining(training);
    }

    onDelete(event: Event, training: Training): void {
        event.stopPropagation();
        if (!confirm(`Delete "${training.title}"?`)) return;
        this.trainingService.deleteTraining(training.id).subscribe({
            next: () => this.trainingService.removeTrainingLocally(training.id),
            error: () => this.trainingService.removeTrainingLocally(training.id),
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

    getDuration(training: Training): string {
        if (!training.blocks || training.blocks.length === 0) return '';
        const totalSec =
            training.estimatedDurationSeconds ||
            (training.blocks ? training.blocks.reduce((sum, b) => sum + (b.durationSeconds || 0), 0) : 0);
        const h = Math.floor(totalSec / 3600);
        const m = Math.floor((totalSec % 3600) / 60);
        if (h > 0) return `${h}h ${m}m`;
        return `${m}m`;
    }

    getTypeColor(type: TrainingType): string {
        return TRAINING_TYPE_COLORS[type] || '#888';
    }

    getTypeLabel(type: TrainingType): string {
        return TRAINING_TYPE_LABELS[type] || type;
    }
}
