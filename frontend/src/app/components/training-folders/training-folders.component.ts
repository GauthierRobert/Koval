import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Training, TrainingService, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';

@Component({
  selector: 'app-training-folders',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Collapsed: folder cards row -->
    <div class="folders-row" *ngIf="!expandedFolder && folderNames.length">
      <h2 class="section-title">Coach Trainings</h2>
      <div class="folder-cards">
        <div
          *ngFor="let name of folderNames"
          class="folder-card glass"
          (click)="expand(name)"
        >
          <div class="folder-icon">&#128193;</div>
          <div class="folder-name">{{ name }}</div>
          <div class="folder-count">{{ folders[name].length }} training{{ folders[name].length === 1 ? '' : 's' }}</div>
        </div>
      </div>
    </div>

    <!-- Expanded: training list for a folder -->
    <div class="folder-expanded" *ngIf="expandedFolder">
      <div class="folder-header">
        <button class="back-btn" (click)="collapse()">&larr;</button>
        <h2 class="section-title">{{ expandedFolder }}</h2>
      </div>
      <div class="training-cards" *ngIf="folders[expandedFolder]?.length">
        <div
          *ngFor="let t of folders[expandedFolder]"
          class="training-card glass"
          (click)="selectTraining(t)"
        >
          <div class="card-top">
            <span
              *ngIf="t.trainingType"
              class="type-badge"
              [style.background]="getTypeColor(t.trainingType) + '20'"
              [style.color]="getTypeColor(t.trainingType)"
              [style.border-color]="getTypeColor(t.trainingType) + '40'"
            >{{ getTypeLabel(t.trainingType) }}</span>
            <span class="card-duration">{{ formatDuration(t) }}</span>
          </div>
          <div class="card-title">{{ t.title }}</div>
          <div class="card-actions">
            <button class="schedule-btn" (click)="schedule(t, $event)">SCHEDULE</button>
          </div>
        </div>
      </div>
      <div class="empty-folder" *ngIf="!folders[expandedFolder]?.length">
        No trainings shared in this group yet
      </div>
    </div>
  `,
  styles: [`
    .folders-row {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .section-title {
      font-size: 0.85rem;
      font-weight: 800;
      letter-spacing: -0.5px;
      color: var(--text-color);
      margin: 0;
    }

    .folder-cards {
      display: flex;
      gap: 10px;
      overflow-x: auto;
      padding-bottom: 2px;
    }

    .folder-card {
      min-width: 140px;
      padding: 12px 16px;
      border-radius: 10px;
      border: 1px solid var(--glass-border);
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex-shrink: 0;
      background: var(--surface-elevated);
    }

    .folder-card:hover {
      background: var(--surface-hover);
      border-color: rgba(255, 157, 0, 0.3);
      transform: translateY(-2px);
    }

    .folder-icon { font-size: 1.3rem; }

    .folder-name {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-color);
    }

    .folder-count {
      font-size: 0.65rem;
      color: var(--text-muted);
      font-weight: 600;
    }

    .folder-expanded {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .folder-header {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .back-btn {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: var(--text-color);
      width: 28px;
      height: 28px;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
    }

    .back-btn:hover { background: rgba(255, 255, 255, 0.12); }

    .training-cards {
      display: flex;
      gap: 10px;
      overflow-x: auto;
      padding-bottom: 2px;
    }

    .training-card {
      min-width: 200px;
      padding: 12px 14px;
      border-radius: 10px;
      border: 1px solid var(--glass-border);
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex-shrink: 0;
      background: var(--surface-elevated);
    }

    .training-card:hover {
      background: var(--surface-hover);
      border-color: rgba(255, 157, 0, 0.3);
      transform: translateY(-2px);
    }

    .card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .type-badge {
      font-size: 8px;
      font-weight: 800;
      padding: 2px 6px;
      border-radius: 6px;
      border: 1px solid;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .card-duration {
      font-size: 0.65rem;
      color: var(--text-muted);
      font-weight: 600;
    }

    .card-title {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-color);
      line-height: 1.2;
    }

    .card-actions {
      display: flex;
      gap: 6px;
      margin-top: 4px;
    }

    .schedule-btn {
      background: rgba(255, 157, 0, 0.08);
      border: 1px solid rgba(255, 157, 0, 0.2);
      color: var(--accent-color);
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 9px;
      font-weight: 800;
      cursor: pointer;
      transition: all 0.2s;
    }

    .schedule-btn:hover { background: rgba(255, 157, 0, 0.15); }

    .empty-folder {
      padding: 16px;
      border-radius: 10px;
      border: 1px dashed rgba(255, 255, 255, 0.1);
      background: var(--surface-elevated);
      color: var(--text-muted);
      font-size: 0.8rem;
      font-weight: 600;
    }
  `]
})
export class TrainingFoldersComponent implements OnInit {
  @Output() scheduleTraining = new EventEmitter<Training>();
  @Output() trainingSelected = new EventEmitter<Training>();

  folders: Record<string, Training[]> = {};
  folderNames: string[] = [];
  expandedFolder: string | null = null;

  constructor(private trainingService: TrainingService) {}

  ngOnInit(): void {
    this.trainingService.getTrainingFolders().subscribe({
      next: (data) => {
        this.folders = data;
        this.folderNames = Object.keys(data).sort();
      },
      error: () => {
        this.folders = {};
        this.folderNames = [];
      },
    });
  }

  expand(folder: string): void {
    this.expandedFolder = folder;
  }

  collapse(): void {
    this.expandedFolder = null;
  }

  selectTraining(training: Training): void {
    this.trainingSelected.emit(training);
  }

  schedule(training: Training, event: Event): void {
    event.stopPropagation();
    this.scheduleTraining.emit(training);
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  formatDuration(training: Training): string {
    if (!training.blocks || training.blocks.length === 0) return '';
    const totalSec = training.blocks.reduce((sum, b) => sum + (b.durationSeconds || 0), 0);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }
}
