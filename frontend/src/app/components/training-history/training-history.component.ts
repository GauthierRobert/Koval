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

    readonly trainingTypes = TRAINING_TYPES;

    selectedTraining$ = this.trainingService.selectedTraining$;

    // Tag folders
    folders: Record<string, Training[]> = {};
    folderNames: string[] = [];
    expandedFolder: string | null = null;

    private typeFilterSubject = new BehaviorSubject<TrainingType | null>(null);
    activeTypeFilter$ = this.typeFilterSubject.asObservable();

    filteredTrainings$: Observable<Training[]> = combineLatest([
        this.trainingService.trainings$,
        this.typeFilterSubject,
    ]).pipe(
        map(([trainings, typeFilter]) => {
            if (!typeFilter) return trainings;
            return trainings.filter((t) => t.trainingType === typeFilter);
        })
    );

    ngOnInit(): void {
        // Load folders when user tags become available
        this.authService.user$.pipe(
            filter(u => !!u && !!u.tags?.length),
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

    getDuration(training: Training): string {
        if (!training.blocks || training.blocks.length === 0) return '';
        const totalSec = training.estimatedDurationSeconds || (training.blocks ? training.blocks.reduce((sum, b) => sum + (b.durationSeconds || 0) * (b.repeats || 1), 0) : 0);
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

    setTypeFilter(type: TrainingType): void {
        const current = this.typeFilterSubject.value;
        this.typeFilterSubject.next(current === type ? null : type);
    }

    clearTypeFilter(): void {
        this.typeFilterSubject.next(null);
    }
}
