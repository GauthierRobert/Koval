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

  @Output() rpeChanged = new EventEmitter<number>();

  rpeValues = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  selectRpe(val: number): void {
    this.rpeChanged.emit(val);
  }

  formatTime(seconds: number): string {
    return formatTimeHMS(seconds);
  }

  formatSpeed(speedMs: number, sportType: string): string {
    if (!speedMs || speedMs <= 0) return '\u2014';
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
    return new Date(date).toLocaleDateString('en-US', {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
