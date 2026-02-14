import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Training, TrainingService, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';

@Component({
  selector: 'app-training-folders',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './training-folders.component.html',
  styleUrl: './training-folders.component.css'
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
