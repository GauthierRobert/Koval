import {Component, DestroyRef, inject, OnInit} from '@angular/core';
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
import {combineLatest, Observable} from 'rxjs';
import {distinctUntilChanged, filter, map, pairwise, take} from 'rxjs/operators';
import {RouterModule} from '@angular/router';
import {ResponsiveService} from '../../../services/responsive.service';
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
})
export class WorkoutSelectionComponent implements OnInit {
  private trainingService = inject(TrainingService);
  filterService = inject(TrainingFilterService);
  private translate = inject(TranslateService);
  private responsive = inject(ResponsiveService);
  private destroyRef = inject(DestroyRef);

  showAiModal = false;
  mobileListOpen = true;
  private isMobile = false;

  showMobileList(): void {
    this.mobileListOpen = true;
  }

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

  selectedTraining$: Observable<Training | null> = this.trainingService.selectedTraining$;

  ngOnInit(): void {
    this.trainingService.loadReceivedTrainings();

    this.responsive.isMobile$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((mobile) => {
      this.isMobile = mobile;
      if (!mobile) this.mobileListOpen = true;
    });

    // Switch to detail view when a training is selected on mobile
    this.selectedTraining$.pipe(
      distinctUntilChanged((a, b) => a?.id === b?.id),
      pairwise(),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(([prev, curr]) => {
      if (this.isMobile && curr && prev?.id !== curr.id) {
        this.mobileListOpen = false;
      }
    });

    // Auto-select first training if none is selected
    combineLatest([this.filterService.filteredTrainings$, this.selectedTraining$]).pipe(
      filter(([trainings]) => trainings.length > 0),
      take(1),
    ).subscribe(([trainings, selected]) => {
      if (!selected) {
        this.trainingService.selectTraining(trainings[0]);
      }
    });
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
