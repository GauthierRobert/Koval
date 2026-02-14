import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Training, TrainingService } from '../../services/training.service';
import { Tag } from '../../services/tag.service';

@Component({
  selector: 'app-share-training-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './share-training-modal.component.html',
  styleUrl: './share-training-modal.component.css'
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
