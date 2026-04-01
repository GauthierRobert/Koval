import {Component, ChangeDetectionStrategy, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {Training} from '../../../../models/training.model';
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
}
