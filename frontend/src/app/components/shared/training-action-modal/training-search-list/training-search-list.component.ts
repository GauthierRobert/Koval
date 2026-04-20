import {Component, ChangeDetectionStrategy, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {Training, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType} from '../../../../models/training.model';
import {SPORT_BANNER_COLORS} from '../../../../models/plan.model';
import {ClubGroup} from '../../../../services/club.service';

@Component({
  selector: 'app-training-search-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './training-search-list.component.html',
  styleUrl: './training-search-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrainingSearchListComponent {
  @Input() trainings: Training[] = [];
  @Input() selectedTrainingId: string | null = null;
  @Input() searchQuery = '';
  @Input() mode = '';
  @Input() availableGroups: ClubGroup[] = [];
  @Input() selectedGroupId = '';
  @Input() sessionAvailableGroups: ClubGroup[] = [];
  @Input() sessionShowNoGroupOption = true;
  @Input() loading = false;

  @Output() searchChange = new EventEmitter<string>();
  @Output() selectTraining = new EventEmitter<Training>();
  @Output() groupChange = new EventEmitter<string>();

  get filteredTrainings(): Training[] {
    if (!this.searchQuery.trim()) return this.trainings;
    const q = this.searchQuery.toLowerCase();
    return this.trainings.filter(
      (t) =>
        t.title?.toLowerCase().includes(q) ||
        t.description?.toLowerCase().includes(q)
    );
  }

  onSearchChange(value: string): void {
    this.searchChange.emit(value);
  }

  onSelectTraining(t: Training): void {
    this.selectTraining.emit(t);
  }

  onGroupChange(value: string): void {
    this.groupChange.emit(value);
  }

  sportColor(sport: string): { bg: string; border: string; text: string } {
    return SPORT_BANNER_COLORS[sport] ?? { bg: 'rgba(255,157,0,0.15)', border: '#ff9d00', text: '#ff9d00' };
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }
}
