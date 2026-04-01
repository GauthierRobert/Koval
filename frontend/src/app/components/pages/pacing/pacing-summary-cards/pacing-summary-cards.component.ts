import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PacingSummary } from '../../../../services/pacing.service';

@Component({
  selector: 'app-pacing-summary-cards',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './pacing-summary-cards.component.html',
  styleUrl: './pacing-summary-cards.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacingSummaryCardsComponent {
  @Input() swimSummary: PacingSummary | null = null;
  @Input() bikeSummary: PacingSummary | null = null;
  @Input() runSummary: PacingSummary | null = null;
  @Input() bikeShowSpeed = false;
  @Input() showGroupMean = false;

  @Output() bikeShowSpeedChange = new EventEmitter<boolean>();
  @Output() showGroupMeanChange = new EventEmitter<boolean>();

  formatTime(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.round(seconds % 60);
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  formatDistance(meters: number): string {
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }

  toggleBikeShowSpeed(): void {
    this.bikeShowSpeedChange.emit(!this.bikeShowSpeed);
  }

  toggleShowGroupMean(): void {
    this.showGroupMeanChange.emit(!this.showGroupMean);
  }
}
