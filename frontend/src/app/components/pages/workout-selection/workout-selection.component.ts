import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  TrainingService,
  Training,
  TrainingType,
  TrainingSource,
  TRAINING_TYPES,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  SPORT_OPTIONS,
  SportFilter,
} from '../../../services/training.service';
import { Observable, map } from 'rxjs';
import { RouterModule } from '@angular/router';
import { WorkoutVisualizationComponent } from '../../shared/workout-visualization/workout-visualization.component';
import { SidebarComponent } from '../../layout/sidebar/sidebar.component';
import { FilterPillsComponent, FilterPillOption } from '../../shared/filter-pills/filter-pills.component';
import { CreateWithAiModalComponent } from '../../shared/create-with-ai-modal/create-with-ai-modal.component';
import { ActionResult } from '../../../services/ai-action.service';

@Component({
  selector: 'app-workout-selection',
  standalone: true,
  imports: [
    CommonModule,
    WorkoutVisualizationComponent,
    RouterModule,
    SidebarComponent,
    FilterPillsComponent,
    CreateWithAiModalComponent,
  ],
  templateUrl: './workout-selection.component.html',
  styleUrl: './workout-selection.component.css',
})
export class WorkoutSelectionComponent {
  trainingService = inject(TrainingService);

  showAiModal = false;

  readonly sourceOptions: FilterPillOption[] = [
    { label: 'My Trainings', value: 'mine' },
    { label: 'Club', value: 'club' },
  ];

  clubGroupOptions$: Observable<FilterPillOption[]> = this.trainingService.clubGroupMap$.pipe(
    map((groupMap) =>
      Array.from(groupMap.entries()).map(([id, name]) => ({ label: name, value: id })),
    ),
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

  tagOptions$: Observable<FilterPillOption[]> = this.trainingService.availableTags$.pipe(
    map((tags) => [
      { label: 'My Workouts', value: '__mine__' } as FilterPillOption,
      ...tags.map((tag) => ({ label: tag, value: tag }) as FilterPillOption),
    ]),
  );

  selectedTraining$: Observable<Training | null> = this.trainingService.selectedTraining$;

  onSportChange(value: string | null): void {
    this.trainingService.setSportFilter(value as SportFilter);
  }

  onTypeChange(value: string | null): void {
    this.trainingService.setTypeFilter(value as TrainingType);
  }

  setTagFilter(value: string | null): void {
    this.trainingService.setTagFilter(value as string);
  }

  onSourceChange(value: string | null): void {
    if (value) this.trainingService.setSource(value as TrainingSource);
  }

  onClubGroupChange(value: string | null): void {
    this.trainingService.setClubGroupFilter(value);
  }

  onAiCreated(_result: ActionResult): void {
    this.showAiModal = false;
    this.trainingService.loadTrainings();
  }
}
