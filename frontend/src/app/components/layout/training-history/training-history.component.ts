import { Component, inject, OnInit } from '@angular/core';

import { CommonModule } from '@angular/common';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import {
    TrainingService,
    Training,
    TrainingType,
    TRAINING_TYPE_COLORS,
    TRAINING_TYPE_LABELS,
    hasDurationEstimate,
} from '../../../services/training.service';
import { DurationEstimationService } from '../../../services/duration-estimation.service';
import { AuthService } from '../../../services/auth.service';
import { HistoryService } from '../../../services/history.service';
import { filter } from 'rxjs/operators';

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
    private durationService = inject(DurationEstimationService);

    selectedTraining$ = this.trainingService.selectedTraining$;
    filteredTrainings$ = this.trainingService.filteredTrainings$;
    activeSource$ = this.trainingService.activeSource$;

    // Tag folders
    folders: Record<string, Training[]> = {};
    folderNames: string[] = [];
    expandedFolder: string | null = null;

    ngOnInit(): void {
        this.authService.user$.pipe(
            filter((u) => !!u && !!u.groups?.length),
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
        const totalSec =
            training.estimatedDurationSeconds ||
            training.blocks.reduce((sum, b) => sum + this.durationService.estimateDuration(b, training, null), 0);
        if (totalSec === 0) return '';
        const h = Math.floor(totalSec / 3600);
        const m = Math.floor((totalSec % 3600) / 60);
        if (h > 0) return `${h}h ${m}m`;
        return `${m}m`;
    }

    isDurationEstimated(training: Training): boolean {
        return hasDurationEstimate(training);
    }

    getTypeColor(type: TrainingType): string {
        return TRAINING_TYPE_COLORS[type] || '#888';
    }

    getTypeLabel(type: TrainingType): string {
        return TRAINING_TYPE_LABELS[type] || type;
    }
}
