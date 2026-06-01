import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export type MetricToggleKey = 'showPrimary' | 'showHR' | 'showCadence' | 'showSpeed' | 'showDrift';

/**
 * Metric visibility toggle strip for the FIT time-series chart (power/pace, speed, HR,
 * cadence, cardiac drift). Presentational only: the parent owns the active state and the
 * data-availability flags, this component renders the buttons and emits toggle intents.
 */
@Component({
  selector: 'app-metric-toggles',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metric-toggles.component.html',
  styleUrl: './metric-toggles.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricTogglesComponent {
  @Input() sportType = 'CYCLING';
  @Input() hasPower = false;
  @Input() hasSpeed = false;
  @Input() hasCadence = false;
  @Input() hasDrift = false;

  @Input() showPrimary = true;
  @Input() showHR = true;
  @Input() showCadence = false;
  @Input() showSpeed = true;
  @Input() showDrift = true;

  @Output() metricToggled = new EventEmitter<MetricToggleKey>();

  get isCycling(): boolean {
    return this.sportType === 'CYCLING';
  }
  get isSwimming(): boolean {
    return this.sportType === 'SWIMMING';
  }
  get primaryLabel(): string {
    if (this.isCycling) return 'Power';
    if (this.isSwimming) return 'Pace';
    return 'Speed';
  }
}
