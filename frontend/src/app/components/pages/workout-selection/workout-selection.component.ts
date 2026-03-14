import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainingService } from '../../../services/training.service';
import { TrainingFilterService } from '../../../services/training-filter.service';
import {
  Training,
  TrainingType,
  TRAINING_TYPES,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  SPORT_OPTIONS,
  SportFilter,
} from '../../../models/training.model';
import { combineLatest, Observable, of } from 'rxjs';
import { catchError, map, startWith } from 'rxjs/operators';
import { RouterModule } from '@angular/router';
import { WorkoutVisualizationComponent } from '../../shared/workout-visualization/workout-visualization.component';
import { SidebarComponent } from '../../layout/sidebar/sidebar.component';
import { FilterPillsComponent, FilterPillOption } from '../../shared/filter-pills/filter-pills.component';
import { CreateWithAiModalComponent } from '../../shared/create-with-ai-modal/create-with-ai-modal.component';
import { ActionResult } from '../../../services/ai-action.service';
import { ClubService } from '../../../services/club.service';
import { GroupService, Group } from '../../../services/group.service';

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
export class WorkoutSelectionComponent implements OnInit {
  private trainingService = inject(TrainingService);
  filterService = inject(TrainingFilterService);
  private clubService = inject(ClubService);
  private groupService = inject(GroupService);

  showAiModal = false;

  sourceOptions$: Observable<FilterPillOption[]> = combineLatest([
    this.clubService.userClubs$,
    this.groupService.getGroups().pipe(
      startWith([] as Group[]),
      catchError(() => of([] as Group[])),
    ),
  ]).pipe(
    map(([clubs, groups]) => [
      { label: 'My Trainings', value: 'mine' },
      ...clubs.map((c) => ({ label: c.name, value: `club:${c.id}` })),
      ...groups.map((g) => ({ label: g.name, value: `group:${g.id}` })),
    ]),
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
      { label: 'My Workouts', value: '__mine__' } as FilterPillOption,
      ...tags.map((tag) => ({ label: tag, value: tag }) as FilterPillOption),
    ]),
  );

  selectedTraining$: Observable<Training | null> = this.trainingService.selectedTraining$;

  ngOnInit(): void {
    this.clubService.loadUserClubs();
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

  onAiCreated(_result: ActionResult): void {
    this.showAiModal = false;
    this.trainingService.loadTrainings();
  }
}
