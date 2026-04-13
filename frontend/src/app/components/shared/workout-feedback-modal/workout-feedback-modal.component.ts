import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ModalShellComponent} from '../modal-shell/modal-shell.component';
import {HistoryService, SessionFeedback} from '../../../services/history.service';

@Component({
  selector: 'app-workout-feedback-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, ModalShellComponent],
  templateUrl: './workout-feedback-modal.component.html',
  styleUrl: './workout-feedback-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutFeedbackModalComponent {
  @Input() sessionId!: string;
  @Output() submitted = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  private historyService = inject(HistoryService);

  difficultyRating = 3;
  enjoymentRating = 3;
  notes = '';
  saving = false;

  difficultyLabels = ['Very Easy', 'Easy', 'Moderate', 'Hard', 'Very Hard'];
  enjoymentLabels = ['Hated it', 'Disliked', 'Neutral', 'Enjoyed', 'Loved it'];

  setDifficulty(value: number): void {
    this.difficultyRating = value;
  }

  setEnjoyment(value: number): void {
    this.enjoymentRating = value;
  }

  submit(): void {
    this.saving = true;
    const feedback: SessionFeedback = {
      difficultyRating: this.difficultyRating,
      enjoymentRating: this.enjoymentRating,
      notes: this.notes.trim() || undefined,
    };
    this.historyService.submitFeedback(this.sessionId, feedback).subscribe({
      next: () => {
        this.saving = false;
        this.submitted.emit();
      },
      error: () => {
        this.saving = false;
        this.closed.emit();
      },
    });
  }

  skip(): void {
    this.closed.emit();
  }
}
