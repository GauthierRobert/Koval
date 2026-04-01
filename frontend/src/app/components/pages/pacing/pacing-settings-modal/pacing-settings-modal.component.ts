import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AthleteProfile } from '../../../../services/pacing.service';
import { GpxUploadZoneComponent } from '../gpx-upload-zone/gpx-upload-zone.component';
import { DisciplineSelectorComponent } from '../discipline-selector/discipline-selector.component';

@Component({
  selector: 'app-pacing-settings-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, GpxUploadZoneComponent, DisciplineSelectorComponent],
  templateUrl: './pacing-settings-modal.component.html',
  styleUrl: './pacing-settings-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacingSettingsModalComponent {
  @Input() isOpen = false;
  @Input() profile!: AthleteProfile;
  @Input() discipline = 'TRIATHLON';
  @Input() gpxFileName = '';
  @Input() bikeGpxFileName = '';
  @Input() runGpxFileName = '';
  @Input() bikeLoops = 1;
  @Input() runLoops = 1;
  @Input() targetPowerPct: number | null = null;
  @Input() targetPacePct: number | null = null;
  @Input() computedPowerWatts: number | null = null;
  @Input() computedPaceDisplay: string | null = null;
  @Input() loading = false;
  @Input() blockedReason: string | null = null;
  @Input() error: string | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() regenerate = new EventEmitter<void>();
  @Output() disciplineChange = new EventEmitter<string>();
  @Output() bikeTypeChange = new EventEmitter<string>();
  @Output() profileChange = new EventEmitter<AthleteProfile>();
  @Output() targetPowerPctChange = new EventEmitter<number | null>();
  @Output() targetPacePctChange = new EventEmitter<number | null>();
  @Output() gpxFileSelected = new EventEmitter<File>();
  @Output() bikeGpxFileSelected = new EventEmitter<File>();
  @Output() runGpxFileSelected = new EventEmitter<File>();
  @Output() bikeLoopsChange = new EventEmitter<number>();
  @Output() runLoopsChange = new EventEmitter<number>();

  needsSwim(): boolean {
    return this.discipline === 'SWIM' || this.discipline === 'TRIATHLON';
  }

  needsBike(): boolean {
    return this.discipline === 'BIKE' || this.discipline === 'TRIATHLON';
  }

  needsRun(): boolean {
    return this.discipline === 'RUN' || this.discipline === 'TRIATHLON';
  }

  canGenerate(): boolean {
    return !this.blockedReason && !this.loading;
  }

  onProfileFieldChange(): void {
    this.profileChange.emit({ ...this.profile });
  }
}
