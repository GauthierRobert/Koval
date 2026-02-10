import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainingService, Training } from '../../services/training.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-training-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="history-container">
      <h3 class="section-title">Your Workouts</h3>
      <div class="history-list">
        <div
          *ngFor="let training of trainings$ | async"
          class="history-item"
          [class.active]="(selectedTraining$ | async)?.id === training.id"
          (click)="onSelect(training)"
        >
          <div class="item-icon">⚡</div>
          <div class="item-details">
            <span class="item-title">{{ training.title }}</span>
            <span class="item-meta">{{ getDuration(training) }} • {{ training.blocks.length }} blocks</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .history-container {
      padding: 12px 0;
    }
    .section-title {
      font-size: 12px;
      text-transform: uppercase;
      color: var(--text-muted);
      letter-spacing: 1px;
      margin-bottom: 16px;
      padding-left: 12px;
    }
    .history-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .history-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px;
      border-radius: 12px;
      cursor: pointer;
      transition: background 0.2s;
    }
    .history-item:hover {
      background: rgba(255, 255, 255, 0.05);
    }
    .history-item.active {
      background: rgba(255, 157, 0, 0.1);
      border: 1px solid rgba(255, 157, 0, 0.2);
    }
    .item-icon {
      font-size: 20px;
    }
    .item-details {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .item-title {
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .item-meta {
      font-size: 11px;
      color: var(--text-muted);
    }
  `]
})
export class TrainingHistoryComponent {
  trainings$: Observable<Training[]>;
  selectedTraining$: Observable<Training | null>;

  constructor(private trainingService: TrainingService) {
    this.trainings$ = this.trainingService.trainings$;
    this.selectedTraining$ = this.trainingService.selectedTraining$;
  }

  onSelect(training: Training) {
    this.trainingService.selectTraining(training);
  }

  getDuration(training: Training): string {
    const totalSeconds = training.blocks.reduce((acc, block) => acc + block.durationSeconds, 0);
    const minutes = Math.floor(totalSeconds / 60);
    return `${minutes} min`;
  }
}
