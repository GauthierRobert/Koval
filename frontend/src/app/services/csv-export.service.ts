import { Injectable, inject } from '@angular/core';
import { SavedSession } from './history.service';
import { MetricsService } from './metrics.service';

const HEADERS = [
  'title',
  'date',
  'sportType',
  'totalDuration',
  'avgPower',
  'avgHR',
  'avgCadence',
  'avgSpeed',
  'tss',
  'intensityFactor',
  'rpe',
];

@Injectable({ providedIn: 'root' })
export class CsvExportService {
  private metricsService = inject(MetricsService);

  exportSessions(sessions: SavedSession[], ftp: number | null): void {
    const rows: string[] = [HEADERS.join(',')];
    for (const s of sessions) {
      const tss = this.computeTss(s, ftp);
      const intensity = this.computeIf(s, ftp);
      rows.push(
        [
          this.escape(s.title),
          this.escape(this.toIsoDate(s.date)),
          this.escape(s.sportType ?? ''),
          this.numeric(s.totalDuration),
          this.numeric(s.avgPower),
          this.numeric(s.avgHR),
          this.numeric(s.avgCadence),
          this.numeric(s.avgSpeed),
          this.numeric(tss),
          this.numeric(intensity),
          this.numeric(s.rpe),
        ].join(','),
      );
    }

    // BOM prefix for Excel UTF-8 compatibility
    const csv = '\uFEFF' + rows.join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `workout-history-${this.toIsoDate(new Date())}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  private computeTss(session: SavedSession, ftp: number | null): number | null {
    if (session.tss != null) return Math.round(session.tss);
    if (!ftp) return null;
    return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
  }

  private computeIf(session: SavedSession, ftp: number | null): number | null {
    if (session.intensityFactor != null) return session.intensityFactor;
    if (!ftp) return null;
    return this.metricsService.computeIF(session.avgPower, ftp);
  }

  private toIsoDate(date: Date | string): string {
    const d = date instanceof Date ? date : new Date(date);
    if (isNaN(d.getTime())) return '';
    return d.toISOString().slice(0, 10);
  }

  private numeric(value: number | null | undefined): string {
    return value == null ? '' : String(value);
  }

  /**
   * RFC 4180: wrap field in double-quotes if it contains comma, quote, or newline.
   * Escape embedded double-quotes by doubling them.
   */
  private escape(value: string): string {
    if (value == null) return '';
    if (/[",\r\n]/.test(value)) {
      return '"' + value.replace(/"/g, '""') + '"';
    }
    return value;
  }
}
