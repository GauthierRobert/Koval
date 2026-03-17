import {Component, inject} from '@angular/core';

import {CommonModule} from '@angular/common';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {TrainingService} from '../../../services/training.service';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {
  hasDurationEstimate,
  Training,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  TrainingType,
} from '../../../models/training.model';
import {DurationEstimationService} from '../../../services/duration-estimation.service';
import {HistoryService} from '../../../services/history.service';

@Component({
    selector: 'app-training-history',
    standalone: true,
    imports: [CommonModule, SportIconComponent],
    templateUrl: './training-history.component.html',
    styleUrl: './training-history.component.css',
})
export class TrainingHistoryComponent {
    private trainingService = inject(TrainingService);
    private filterService = inject(TrainingFilterService);
    private historyService = inject(HistoryService);
    private durationService = inject(DurationEstimationService);

    selectedTraining$ = this.trainingService.selectedTraining$;
    filteredTrainings$ = this.filterService.filteredTrainings$;
    activeContext$ = this.filterService.activeContext$;

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
