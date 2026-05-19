import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { SavedSession } from '../../../../services/history.service';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { formatTimeHMS } from '../../../shared/format/format.utils';

@Component({
  selector: 'app-session-stats-header',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './session-stats-header.component.html',
  styleUrl: './session-stats-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionStatsHeaderComponent {
  @Input({ required: true }) session!: SavedSession;
  @Input() tss: number | null = null;
  @Input() intensityFactor: number | null = null;
  @Input() ftp: number | null = null;
  @Input() movingTime: number | null = null;

  /** Sessions linked to the current one (same group). Includes the current session.
   * When length > 1, the title row shows a pill switcher. */
  @Input() linkedSessions: SavedSession[] = [];

  @Output() rpeChanged = new EventEmitter<number>();
  @Output() navTo = new EventEmitter<string>();
  @Output() linkClicked = new EventEmitter<void>();
  @Output() downloadClicked = new EventEmitter<void>();

  rpeValues = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  private static readonly SPORT_COLORS: Record<string, string> = {
    CYCLING: 'var(--success-color)',
    RUNNING: 'var(--danger-color)',
    SWIMMING: 'var(--secondary-color)',
    BRICK: 'var(--accent-color)',
  };

  sportColor(sport: string | null | undefined): string {
    return SessionStatsHeaderComponent.SPORT_COLORS[sport ?? ''] ?? 'var(--accent-color)';
  }

  selectRpe(val: number): void {
    this.rpeChanged.emit(val);
  }

  formatTime(seconds: number): string {
    return formatTimeHMS(seconds);
  }

  formatSpeed(speedMs: number, sportType: string): string {
    if (!speedMs || speedMs <= 0) return '—';
    if (sportType === 'SWIMMING') {
      const secPer100 = 100 / speedMs;
      const m = Math.floor(secPer100 / 60);
      const s = Math.round(secPer100 % 60);
      return `${m}:${String(s).padStart(2, '0')} /100m`;
    }
    const secPerKm = 1000 / speedMs;
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${String(s).padStart(2, '0')} /km`;
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
