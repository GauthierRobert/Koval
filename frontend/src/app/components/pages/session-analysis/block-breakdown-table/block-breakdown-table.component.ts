import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ZoneBlock } from '../../../../services/zone';
import { BlockSummary } from '../../../../services/workout-execution.service';
import { formatTimeHMS } from '../../../shared/format/format.utils';

export interface PlannedBlockWithZone extends BlockSummary {
  zoneLabel?: string;
  zoneColor?: string;
  actualSpeedKmh?: number;
}

@Component({
  selector: 'app-block-breakdown-table',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './block-breakdown-table.component.html',
  styleUrl: './block-breakdown-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BlockBreakdownTableComponent {
  @Input() blockView: 'planned' | 'interpolated' = 'interpolated';
  @Input() plannedBlocks: PlannedBlockWithZone[] = [];
  @Input() zoneBlocks: ZoneBlock[] = [];
  @Input() availableZoneChips: { label: string; color: string }[] = [];
  @Input() zoneFilters: Set<string> = new Set();
  @Input() ftp: number | null = null;
  @Input() sportType: string = 'CYCLING';

  @Output() blockViewChange = new EventEmitter<'planned' | 'interpolated'>();
  @Output() toggleZoneFilter = new EventEmitter<string>();
  @Output() clearZoneFilters = new EventEmitter<void>();

  isZoneActive(label: string): boolean {
    return this.zoneFilters.size === 0 || this.zoneFilters.has(label);
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

  formatZoneDistance(meters: number): string {
    if (meters == null || meters <= 0) return '\u2014';
    if (meters >= 1000) return (meters / 1000).toFixed(2) + ' km';
    return Math.round(meters) + ' m';
  }

  formatTime(seconds: number): string {
    return formatTimeHMS(seconds);
  }

  formatDistance(block: BlockSummary): string {
    const m = block.distanceMeters;
    if (m == null || m <= 0) return '\u2014';
    if (m >= 1000) return (m / 1000).toFixed(2) + ' km';
    return Math.round(m) + ' m';
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
}
