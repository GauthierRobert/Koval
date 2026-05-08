import {ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TrainingService} from '../../../services/training.service';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {
  SPORT_OPTIONS,
  SportFilter,
  Training,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  TRAINING_TYPES,
  TrainingType,
} from '../../../models/training.model';
import {Observable, of} from 'rxjs';
import {catchError, map, shareReplay, switchMap} from 'rxjs/operators';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {WorkoutVisualizationComponent} from '../../shared/workout-visualization/workout-visualization.component';
import {SidebarComponent} from '../../layout/sidebar/sidebar.component';
import {FilterPillOption, FilterPillsComponent} from '../../shared/filter-pills/filter-pills.component';
import {CreateWithAiModalComponent} from '../../shared/create-with-ai-modal/create-with-ai-modal.component';

@Component({
  selector: 'app-workout-selection',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    WorkoutVisualizationComponent,
    RouterModule,
    SidebarComponent,
    FilterPillsComponent,
    CreateWithAiModalComponent,
  ],
  templateUrl: './workout-selection.component.html',
  styleUrl: './workout-selection.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutSelectionComponent implements OnInit {
  private trainingService = inject(TrainingService);
  filterService = inject(TrainingFilterService);
  private translate = inject(TranslateService);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  showAiModal = false;

  /**
   * The detail view is driven by the `:id` URL segment:
   *   /trainings        → list (no detail)
   *   /trainings/:id    → detail of that training
   *
   * Mobile flow falls out for free: when the URL has an id, CSS hides the
   * list; the device back button navigates back to /trainings naturally.
   */
  selectedTraining$: Observable<Training | null> = this.route.paramMap.pipe(
    map((p) => p.get('id')),
    switchMap((id) =>
      id
        ? this.trainingService.getTrainingById(id).pipe(catchError(() => of(null)))
        : of(null),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  /** True when the URL is bare `/trainings` (no id). Used by CSS to flip mobile layout. */
  isListView$: Observable<boolean> = this.route.paramMap.pipe(
    map((p) => !p.has('id')),
  );

  sourceOptions$: Observable<FilterPillOption[]> = this.trainingService.receivedTrainings$.pipe(
    map((received) => {
      const options: FilterPillOption[] = [{ label: this.translate.instant('WORKOUT_SELECTION.SOURCE_MY_TRAININGS'), value: 'mine' }];
      const origins = new Set<string>();
      received.forEach((r) => {
        if (r.originName) origins.add(r.originName);
      });
      origins.forEach((name) => options.push({ label: name, value: name }));
      return options;
    }),
  );

  readonly sportOptions: FilterPillOption[] = SPORT_OPTIONS.map((o) => ({
    label: o.label,
    value: o.value,
  }));

  readonly typeOptions: FilterPillOption[] = TRAINING_TYPES.map((t) => ({
    label: TRAINING_TYPE_LABELS[t] || t,
    value: t,
    color: TRAINING_TYPE_COLORS[t] || '#888',
  }));

  tagOptions$: Observable<FilterPillOption[]> = this.filterService.availableTags$.pipe(
    map((tags) => [
      { label: this.translate.instant('WORKOUT_SELECTION.TAG_MY_WORKOUTS'), value: '__mine__' } as FilterPillOption,
      ...tags.map((tag) => ({ label: tag, value: tag }) as FilterPillOption),
    ]),
  );

  ngOnInit(): void {
    this.trainingService.loadTrainings();
    this.trainingService.loadReceivedTrainings();

    // Keep the service-level selectedTraining in sync with the route param.
    // Components downstream (chart toolbar, edit-mode, etc.) still read it
    // via TrainingService while we phase out the BehaviorSubject pattern.
    this.selectedTraining$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((t) => this.trainingService.selectTraining(t));
  }

  onContextChange(value: string | null): void {
    if (value) this.filterService.setContext(value);
  }

  onSportChange(value: string | null): void {
    this.filterService.setSportFilter(value as SportFilter);
  }

  onTypeChange(value: string | null): void {
    this.filterService.setTypeFilter(value as TrainingType);
  }

  setTagFilter(value: string | null): void {
    this.filterService.setTagFilter(value as string);
  }

  onAiCreated(_result: { success: boolean; content?: string }): void {
    this.showAiModal = false;
    this.trainingService.loadTrainings();
  }
}
