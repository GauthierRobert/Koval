import {ChangeDetectionStrategy, Component, DestroyRef, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {FavoriteStarComponent} from '../../shared/favorite-star/favorite-star.component';
import {TrainingService} from '../../../services/training.service';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {
  hasDurationEstimate,
  Training,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  TrainingType,
} from '../../../models/training.model';
import {map} from 'rxjs/operators';
import {DurationEstimationService} from '../../../services/duration-estimation.service';
import {HistoryService} from '../../../services/history.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';

@Component({
    selector: 'app-training-history',
    standalone: true,
    imports: [CommonModule, RouterLink, SportIconComponent, FavoriteStarComponent, TranslateModule],
    templateUrl: './training-history.component.html',
    styleUrl: './training-history.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrainingHistoryComponent {
    private trainingService = inject(TrainingService);
    private filterService = inject(TrainingFilterService);
    private historyService = inject(HistoryService);
    private durationService = inject(DurationEstimationService);
    private translate = inject(TranslateService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);

    /** Highlight the row whose id is in the URL — the route is the source of truth. */
    activeTrainingId$ = this.route.paramMap.pipe(map((p) => p.get('id')));
    filteredTrainings$ = this.filterService.filteredTrainings$;
    activeContext$ = this.filterService.activeContext$;

    onSelect(training: Training): void {
        this.historyService.selectSession(null);
        this.router.navigate(['/trainings', training.id]);
    }

    onEdit(event: Event, training: Training): void {
        event.stopPropagation();
        this.router.navigate(['/trainings', training.id, 'edit']);
    }

    onDelete(event: Event, training: Training): void {
        event.stopPropagation();
        if (!confirm(this.translate.instant('TRAINING_HISTORY.DELETE_CONFIRM', { title: training.title }))) return;
        const wasActive = this.route.snapshot.paramMap.get('id') === training.id;
        this.trainingService.deleteTraining(training.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.trainingService.removeTrainingLocally(training.id);
                    if (wasActive) this.router.navigate(['/trainings']);
                },
                error: () => {
                    this.trainingService.removeTrainingLocally(training.id);
                    if (wasActive) this.router.navigate(['/trainings']);
                },
            });
    }

    onToggleFavorite(event: Event, training: Training): void {
        event.stopPropagation();
        this.trainingService.toggleFavorite(training.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ error: () => {} });
    }

    onDuplicate(event: Event, training: Training): void {
        event.stopPropagation();
        this.trainingService.duplicateTraining(training.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (copy) => this.router.navigate(['/trainings', copy.id]),
                error: (err) => console.error('Failed to duplicate training', err),
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
