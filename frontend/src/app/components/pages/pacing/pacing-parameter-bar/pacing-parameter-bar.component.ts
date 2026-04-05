import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PacingPlanResponse } from '../../../../services/pacing.service';

@Component({
  selector: 'app-pacing-parameter-bar',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './pacing-parameter-bar.component.html',
  styleUrl: './pacing-parameter-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacingParameterBarComponent {
  @Input() plan!: PacingPlanResponse;
  @Input() gpxFileName = '';
  @Input() bikeGpxFileName = '';
  @Input() runGpxFileName = '';
  @Input() bikeLoops = 1;
  @Input() runLoops = 1;
  @Input() discipline = '';
  @Input() ftp: number | undefined;
  @Input() weightKg: number | undefined;
  @Input() targetPowerPct: number | null = null;
  @Input() computedPowerWatts: number | null = null;
  @Input() targetPacePct: number | null = null;
  @Input() computedPaceDisplay: string | null = null;
  @Input() swimCssSec: number | undefined;
  @Input() bikeType: string | undefined;
  @Input() activeTab = 'BIKE';
  @Input() availableTabs: string[] = [];
  @Input() needsBike = false;
  @Input() needsRun = false;
  @Input() needsSwim = false;

  @Output() tabChange = new EventEmitter<string>();
  @Output() modify = new EventEmitter<void>();
  @Output() clear = new EventEmitter<void>();
}
