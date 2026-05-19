import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ModalShellComponent } from '../../../shared/modal-shell/modal-shell.component';

export type ManualSportType = 'CYCLING' | 'RUNNING' | 'SWIMMING';

export interface ManualSessionInput {
  title: string;
  sportType: ManualSportType;
  /** ISO date-time string in the user's local zone (no offset). Spring parses as LocalDateTime. */
  completedAt: string;
  totalDurationSeconds: number;
  totalDistance: number | null;
  avgPower: number | null;
  avgHR: number | null;
  avgCadence: number | null;
  rpe: number | null;
}

/**
 * Modal form for adding a completed session by hand — used when no FIT file is available
 * (outdoor run on watch with broken sync, club swim, etc.). Distance is collected in km
 * and converted to metres at submit; duration is collected as H/M/S and folded to seconds.
 */
@Component({
  selector: 'app-manual-session-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, ModalShellComponent],
  templateUrl: './manual-session-modal.component.html',
  styleUrl: './manual-session-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ManualSessionModalComponent implements OnChanges {
  @Input() open = false;
  @Output() closed = new EventEmitter<void>();
  @Output() create = new EventEmitter<ManualSessionInput>();

  title = '';
  sportType: ManualSportType = 'CYCLING';
  /** Local YYYY-MM-DDTHH:mm string from <input type="datetime-local">. */
  completedAt = '';
  hours = 1;
  minutes = 0;
  seconds = 0;
  distanceKm: number | null = null;
  avgPower: number | null = null;
  avgHR: number | null = null;
  avgCadence: number | null = null;
  rpe: number | null = null;

  submitting = false;
  formError = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.resetForm();
    }
  }

  private resetForm(): void {
    this.title = '';
    this.sportType = 'CYCLING';
    this.completedAt = this.nowLocalIso();
    this.hours = 1;
    this.minutes = 0;
    this.seconds = 0;
    this.distanceKm = null;
    this.avgPower = null;
    this.avgHR = null;
    this.avgCadence = null;
    this.rpe = null;
    this.submitting = false;
    this.formError = '';
  }

  /** YYYY-MM-DDTHH:mm in local time, suitable as the default value of datetime-local. */
  private nowLocalIso(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  get durationSeconds(): number {
    return (this.hours || 0) * 3600 + (this.minutes || 0) * 60 + (this.seconds || 0);
  }

  get canSubmit(): boolean {
    return !this.submitting && !!this.completedAt && this.durationSeconds > 0;
  }

  onSubmit(): void {
    if (!this.canSubmit) {
      this.formError = !this.completedAt
        ? 'A date is required'
        : 'Duration must be greater than zero';
      return;
    }
    this.formError = '';
    this.submitting = true;
    const payload: ManualSessionInput = {
      title: this.title?.trim() || this.defaultTitle(),
      sportType: this.sportType,
      completedAt: this.completedAt.length === 16 ? `${this.completedAt}:00` : this.completedAt,
      totalDurationSeconds: this.durationSeconds,
      totalDistance: this.distanceKm != null ? this.distanceKm * 1000 : null,
      avgPower: this.avgPower,
      avgHR: this.avgHR,
      avgCadence: this.avgCadence,
      rpe: this.rpe,
    };
    this.create.emit(payload);
  }

  private defaultTitle(): string {
    const label =
      this.sportType === 'CYCLING' ? 'Ride' : this.sportType === 'RUNNING' ? 'Run' : 'Swim';
    return `Manual ${label}`;
  }

  /** Parent calls this to surface a backend error and re-enable the submit button. */
  setError(msg: string): void {
    this.submitting = false;
    this.formError = msg;
  }

  /** Parent calls this once the session is persisted. */
  clearAndClose(): void {
    this.submitting = false;
    this.closed.emit();
  }
}
