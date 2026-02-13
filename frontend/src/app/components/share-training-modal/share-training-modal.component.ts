import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Training, TrainingService } from '../../services/training.service';
import { Tag } from '../../services/tag.service';

@Component({
  selector: 'app-share-training-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()">
      <div class="modal-card glass" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2>SHARE <span class="highlight">TRAINING</span></h2>
          <button class="close-btn" (click)="close()">&times;</button>
        </div>

        <div class="modal-body">
          <div class="field">
            <label>Training</label>
            <select [(ngModel)]="selectedTrainingId" (ngModelChange)="onTrainingSelected()">
              <option value="" disabled>Select a training...</option>
              <option *ngFor="let t of allTrainings" [value]="t.id">{{ t.title }}</option>
            </select>
          </div>

          <div class="field" *ngIf="activeTraining">
            <label>Share with tag groups</label>
            <div class="tag-selector">
              <span
                *ngFor="let tag of availableTags"
                class="tag-chip"
                [class.selected]="selectedTagIds.includes(tag.id)"
                (click)="toggleTag(tag)"
              >{{ tag.name }}</span>
            </div>
            <div class="hint" *ngIf="availableTags.length === 0">
              No tags available. Add tags to your athletes first.
            </div>
          </div>

          <button
            class="save-btn"
            (click)="save()"
            [disabled]="saving || !activeTraining"
          >
            {{ saving ? 'SAVING...' : 'SAVE' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-card {
      width: 420px;
      border-radius: 16px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .modal-header h2 {
      font-size: 14px;
      font-weight: 800;
      letter-spacing: 2px;
    }

    .highlight { color: var(--accent-color); }

    .close-btn {
      background: none;
      border: none;
      color: var(--text-muted);
      font-size: 20px;
      cursor: pointer;
    }

    .modal-body {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field label {
      font-size: 10px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    select {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 8px;
      padding: 8px 12px;
      color: #e0e0e0;
      font-size: 12px;
      font-family: inherit;
      outline: none;
    }

    select:focus { border-color: var(--accent-color); }

    .tag-selector {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    .tag-chip {
      font-size: 10px;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #ccc;
      cursor: pointer;
      transition: all 0.2s;
    }

    .tag-chip:hover {
      background: rgba(255, 157, 0, 0.12);
      color: var(--accent-color);
    }

    .tag-chip.selected {
      background: rgba(255, 157, 0, 0.2);
      border-color: var(--accent-color);
      color: var(--accent-color);
    }

    .hint {
      font-size: 11px;
      color: var(--text-muted);
    }

    .save-btn {
      background: var(--accent-color);
      color: #000;
      border: none;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 11px;
      font-weight: 800;
      cursor: pointer;
      transition: filter 0.2s;
      align-self: flex-start;
    }

    .save-btn:hover { filter: brightness(1.15); }
    .save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
  `]
})
export class ShareTrainingModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() training: Training | null = null;
  @Input() availableTags: Tag[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() shared = new EventEmitter<Training>();

  allTrainings: Training[] = [];
  selectedTrainingId = '';
  activeTraining: Training | null = null;
  selectedTagIds: string[] = [];
  saving = false;

  constructor(private trainingService: TrainingService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.trainingService.trainings$.subscribe(t => this.allTrainings = t);
      if (this.training) {
        this.activeTraining = this.training;
        this.selectedTrainingId = this.training.id;
        this.selectedTagIds = [...(this.training.tags || [])];
      } else {
        this.activeTraining = null;
        this.selectedTrainingId = '';
        this.selectedTagIds = [];
      }
    }
  }

  onTrainingSelected(): void {
    this.activeTraining = this.allTrainings.find(t => t.id === this.selectedTrainingId) || null;
    this.selectedTagIds = [...(this.activeTraining?.tags || [])];
  }

  toggleTag(tag: Tag): void {
    const idx = this.selectedTagIds.indexOf(tag.id);
    if (idx >= 0) {
      this.selectedTagIds.splice(idx, 1);
    } else {
      this.selectedTagIds.push(tag.id);
    }
  }

  save(): void {
    if (!this.activeTraining) return;
    this.saving = true;
    this.trainingService.updateTrainingTags(this.activeTraining.id, this.selectedTagIds).subscribe({
      next: (updated) => {
        this.saving = false;
        this.shared.emit(updated);
        this.close();
      },
      error: () => {
        this.saving = false;
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
