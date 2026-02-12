import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    TrainingService,
    Training,
    TrainingType,
    TRAINING_TYPES,
    TRAINING_TYPE_COLORS,
    TRAINING_TYPE_LABELS,
} from '../../services/training.service';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
    selector: 'app-training-history',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './training-history.component.html',
    styleUrl: './training-history.component.css',
})
export class TrainingHistoryComponent {
    private trainingService = inject(TrainingService);

    readonly trainingTypes = TRAINING_TYPES;

    selectedTraining$ = this.trainingService.selectedTraining$;

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

    onSelect(training: Training): void {
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
        const totalSeconds = training.blocks.reduce((acc, block) => acc + block.durationSeconds, 0);
        const minutes = Math.floor(totalSeconds / 60);
        return `${minutes} min`;
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
