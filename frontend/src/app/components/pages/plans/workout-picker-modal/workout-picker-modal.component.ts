import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TrainingSearchListComponent } from '../../../shared/training-action-modal/training-search-list/training-search-list.component';
import { Training } from '../../../../models/training.model';
import { DayOfWeek, DAY_LABELS } from '../../../../models/plan.model';

@Component({
  selector: 'app-workout-picker-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, TrainingSearchListComponent],
  templateUrl: './workout-picker-modal.component.html',
  styleUrl: './workout-picker-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutPickerModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() weekNumber = 1;
  @Input() dayOfWeek: DayOfWeek = 'MONDAY';
  @Input() trainings: Training[] = [];
  @Input() currentTrainingId?: string;
  @Input() currentNotes?: string;

  @Output() confirmed = new EventEmitter<{ trainingId: string; notes: string } | null>();
  @Output() closed = new EventEmitter<void>();

  searchQuery = '';
  selectedTrainingId: string | null = null;
  notes = '';

  readonly DAY_LABELS = DAY_LABELS;

  ngOnChanges(): void {
    if (this.isOpen) {
      this.selectedTrainingId = this.currentTrainingId ?? null;
      this.notes = this.currentNotes ?? '';
      this.searchQuery = '';
    }
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
  }

  onSelectTraining(training: Training): void {
    this.selectedTrainingId = training.id;
  }

  confirm(): void {
    if (this.selectedTrainingId) {
      this.confirmed.emit({ trainingId: this.selectedTrainingId, notes: this.notes });
    }
    this.close();
  }

  removeWorkout(): void {
    this.confirmed.emit(null);
    this.close();
  }

  close(): void {
    this.closed.emit();
  }
}
