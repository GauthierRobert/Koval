import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ZoneSystem } from '../../../../services/zone';

export interface ZoneDistEntry {
  label: string;
  description: string;
  color: string;
  seconds: number;
  percentage: number;
}

@Component({
  selector: 'app-zone-distribution-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './zone-distribution-panel.component.html',
  styleUrl: './zone-distribution-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoneDistributionPanelComponent {
  @Input({ required: true }) zoneDistribution: ZoneDistEntry[] = [];
  @Input() userZoneSystems: ZoneSystem[] = [];
  @Input() currentZoneSystemId: string | null = null;

  @Output() zoneSystemChange = new EventEmitter<string | null>();

  onZoneSystemChange(value: string | null): void {
    this.zoneSystemChange.emit(value || null);
  }

  formatZoneDuration(seconds: number): string {
    if (seconds >= 3600) {
      const h = Math.floor(seconds / 3600);
      const m = Math.floor((seconds % 3600) / 60);
      return `${h}h ${m}m`;
    }
    if (seconds >= 60) {
      const m = Math.floor(seconds / 60);
      const s = seconds % 60;
      return `${m}m ${s}s`;
    }
    return `${seconds}s`;
  }
}
