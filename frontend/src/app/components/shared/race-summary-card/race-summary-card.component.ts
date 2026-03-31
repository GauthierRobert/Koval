import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {Race} from '../../../services/race.service';
import {SportIconComponent} from '../sport-icon/sport-icon.component';

@Component({
  selector: 'app-race-summary-card',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent],
  templateUrl: './race-summary-card.component.html',
  styleUrl: './race-summary-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RaceSummaryCardComponent {
  @Input({required: true}) race!: Race;
  @Input() isExpanded = false;
  @Input() isAdded = false;
  @Input() isOwnRace = false;
  @Input() isEditing = false;
  @Input() editForm: Partial<Race> = {};
  @Input() isSavingEdit = false;
  @Input() gpxUploading: Record<string, boolean> = {};
  @Input() canSimulate = false;
  @Input() simulateDisabledReason = '';

  @Output() toggleExpand = new EventEmitter<Race>();
  @Output() simulate = new EventEmitter<Race>();
  @Output() addToGoals = new EventEmitter<Race>();
  @Output() startEdit = new EventEmitter<Race>();
  @Output() saveEdit = new EventEmitter<Race>();
  @Output() cancelEdit = new EventEmitter<void>();
  @Output() editFormChange = new EventEmitter<Partial<Race>>();
  @Output() gpxFileSelected = new EventEmitter<{race: Race; discipline: string; event: Event}>();
  @Output() loopsChanged = new EventEmitter<{race: Race; discipline: string; event: Event}>();

  getSportColor(): string {
    switch (this.race.sport?.toUpperCase()) {
      case 'CYCLING': return 'var(--success-color)';
      case 'RUNNING': return '#fb923c';
      case 'SWIMMING': return 'var(--info-text)';
      case 'TRIATHLON': return 'var(--accent-color)';
      default: return 'var(--text-30)';
    }
  }

  isPast(): boolean {
    if (!this.race.scheduledDate) return false;
    return new Date(this.race.scheduledDate + 'T00:00:00') < new Date();
  }

  getGpxDisciplines(): string[] {
    switch (this.race.sport?.toUpperCase()) {
      case 'TRIATHLON': return ['swim', 'bike', 'run'];
      case 'CYCLING': return ['bike'];
      case 'RUNNING': return ['run'];
      case 'SWIMMING': return ['swim'];
      default: return ['bike'];
    }
  }

  hasGpx(discipline: string): boolean {
    switch (discipline) {
      case 'swim': return !!this.race.hasSwimGpx;
      case 'bike': return !!this.race.hasBikeGpx;
      case 'run': return !!this.race.hasRunGpx;
      default: return false;
    }
  }

  isUploading(discipline: string): boolean {
    return !!this.gpxUploading[this.race.id + '_' + discipline];
  }

  formatDistance(meters?: number): string {
    if (!meters) return '';
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }

  hasDisciplineDistances(): boolean {
    return !!(this.race.swimDistanceM || this.race.bikeDistanceM || this.race.runDistanceM);
  }

  getLoopCount(discipline: string): number {
    switch (discipline) {
      case 'swim': return this.race.swimGpxLoops ?? 1;
      case 'bike': return this.race.bikeGpxLoops ?? 1;
      case 'run': return this.race.runGpxLoops ?? 1;
      default: return 1;
    }
  }

  onEditFormFieldChange(field: string, value: any): void {
    this.editFormChange.emit({...this.editForm, [field]: value});
  }
}
