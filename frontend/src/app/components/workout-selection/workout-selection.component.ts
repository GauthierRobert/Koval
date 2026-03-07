import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  TrainingService,
  Training,
  TrainingType,
  TRAINING_TYPES,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  SPORT_OPTIONS,
  SportFilter,
} from '../../services/training.service';
import { Observable } from 'rxjs';
import { RouterModule } from '@angular/router';
import { WorkoutVisualizationComponent } from '../workout-visualization/workout-visualization.component';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-workout-selection',
  standalone: true,
  imports: [CommonModule, WorkoutVisualizationComponent, RouterModule, SidebarComponent],
  templateUrl: './workout-selection.component.html',
  styleUrl: './workout-selection.component.css',
})
export class WorkoutSelectionComponent {
  trainingService = inject(TrainingService);

  readonly sportOptions = SPORT_OPTIONS;
  readonly trainingTypes = TRAINING_TYPES;

  selectedTraining$: Observable<Training | null> = this.trainingService.selectedTraining$;

  getTypeColor(type: TrainingType): string {
    return TRAINING_TYPE_COLORS[type] || '#888';
  }

  getTypeLabel(type: TrainingType): string {
    return TRAINING_TYPE_LABELS[type] || type;
  }

  setSportFilter(value: SportFilter): void {
    this.trainingService.setSportFilter(value);
  }

  setTypeFilter(value: TrainingType): void {
    this.trainingService.setTypeFilter(value);
  }

  setTagFilter(value: string): void {
    this.trainingService.setTagFilter(value);
  }
}
