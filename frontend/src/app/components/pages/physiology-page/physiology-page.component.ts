import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, map, Observable } from 'rxjs';
import { AuthService, User } from '../../../services/auth.service';
import { Zone, ZoneSystem } from '../../../services/zone';
import { ZoneService } from '../../../services/zone.service';
import { formatPace as sharedFormatPace } from '../../shared/format/format.utils';

type Sport = 'CYCLING' | 'RUNNING' | 'SWIMMING';

@Component({
  selector: 'app-physiology-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physiology-page.component.html',
  styleUrl: './physiology-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhysiologyPageComponent implements OnInit {
  user$: Observable<User | null>;

  private activeSport$ = new BehaviorSubject<Sport>('CYCLING');
  private zoneSystems$ = new BehaviorSubject<ZoneSystem[]>([]);

  coachZonesForSport$: Observable<ZoneSystem[]> = combineLatest([
    this.zoneSystems$,
    this.activeSport$,
  ]).pipe(
    map(([systems, sport]) => systems.filter((s) => s.sportType === sport)),
  );

  get activeSport(): Sport {
    return this.activeSport$.value;
  }
  set activeSport(value: Sport) {
    this.activeSport$.next(value);
  }

  readonly zones: Record<Sport, Zone[]> = {
    CYCLING: [
      { label: 'Z1', low: 0, high: 55, description: 'Active Recovery' },
      { label: 'Z2', low: 56, high: 75, description: 'Endurance' },
      { label: 'Z3', low: 76, high: 90, description: 'Tempo' },
      { label: 'Z4', low: 91, high: 105, description: 'Threshold' },
      { label: 'Z5', low: 106, high: 120, description: 'VO2max' },
      { label: 'Z6', low: 121, high: 150, description: 'Anaerobic' },
      { label: 'Z7', low: 151, high: 300, description: 'Neuromuscular' },
    ],
    RUNNING: [
      { label: 'Z1', low: 0, high: 75, description: 'Easy' },
      { label: 'Z2', low: 76, high: 85, description: 'Aerobic' },
      { label: 'Z3', low: 86, high: 95, description: 'Tempo' },
      { label: 'Z4', low: 96, high: 105, description: 'Threshold' },
      { label: 'Z5', low: 106, high: 120, description: 'VO2max' },
    ],
    SWIMMING: [
      { label: 'Z1', low: 0, high: 80, description: 'Recovery' },
      { label: 'Z2', low: 81, high: 90, description: 'Endurance' },
      { label: 'Z3', low: 91, high: 100, description: 'Threshold' },
      { label: 'Z4', low: 101, high: 110, description: 'VO2max' },
      { label: 'Z5', low: 111, high: 130, description: 'Sprint' },
    ],
  };

  readonly zoneColors = [
    '#6366f1', '#3b82f6', '#22c55e', '#eab308', '#f97316', '#ef4444', '#dc2626',
  ];

  customRefInputs: Record<string, number | null> = {};

  constructor(
    private authService: AuthService,
    private zoneService: ZoneService,
  ) {
    this.user$ = this.authService.user$;
  }

  ngOnInit(): void {
    this.zoneService.getMyZoneSystems().subscribe({
      next: (systems) => this.zoneSystems$.next(systems),
      error: () => this.zoneSystems$.next([]),
    });
  }

  getZoneColor(i: number): string {
    return this.zoneColors[i % this.zoneColors.length];
  }

  getZoneBarFlex(zone: Zone): number {
    return zone.high - zone.low;
  }

  getActualRange(zone: Zone, user: User, sport: Sport): string | null {
    if (sport === 'CYCLING') {
      const ftp = user.ftp;
      if (!ftp) return null;
      const lowW = Math.round(ftp * zone.low / 100);
      const highW = Math.round(ftp * zone.high / 100);
      const isLast = zone.high >= 200;
      if (isLast) return `${lowW}W+`;
      if (zone.low === 0) return `0–${highW}W`;
      return `${lowW}–${highW}W`;
    }

    const refPace = sport === 'RUNNING' ? user.functionalThresholdPace : user.criticalSwimSpeed;
    if (!refPace) return null;

    // Lower % = slower (higher seconds). Zone boundaries: slow end = refPace / (low/100), fast end = refPace / (high/100)
    const slowSecs = zone.low === 0 ? null : refPace / (zone.low / 100);
    const fastSecs = refPace / (zone.high / 100);

    if (slowSecs === null) return `< ${this.formatPace(fastSecs)}`;
    return `${this.formatPace(slowSecs)}–${this.formatPace(fastSecs)}`;
  }

  formatPace(totalSeconds: number): string {
    return sharedFormatPace(totalSeconds);
  }

  getActualRangeForSystem(zone: Zone, user: User, sys: ZoneSystem): string | null {
    switch (sys.referenceType) {
      case 'FTP':               return this.computeWatts(zone, user.ftp);
      case 'VO2MAX_POWER':      return this.computeWatts(zone, user.vo2maxPower);
      case 'THRESHOLD_PACE':    return this.computePace(zone, user.functionalThresholdPace);
      case 'CSS':               return this.computePace(zone, user.criticalSwimSpeed);
      case 'PACE_5K':           return this.computePace(zone, user.pace5k);
      case 'PACE_10K':          return this.computePace(zone, user.pace10k);
      case 'PACE_HALF_MARATHON':return this.computePace(zone, user.paceHalfMarathon);
      case 'PACE_MARATHON':     return this.computePace(zone, user.paceMarathon);
      case 'CUSTOM': {
        const val = user.customZoneReferenceValues?.[sys.id!];
        return val ? this.computeWatts(zone, val) : null;
      }
      default: return null;
    }
  }

  private computeWatts(zone: Zone, ref: number | undefined): string | null {
    if (!ref) return null;
    const lo = Math.round(ref * zone.low / 100);
    const hi = Math.round(ref * zone.high / 100);
    return zone.high >= 200 ? `${lo}W+` : zone.low === 0 ? `0–${hi}W` : `${lo}–${hi}W`;
  }

  private computePace(zone: Zone, ref: number | undefined): string | null {
    if (!ref) return null;
    const slow = zone.low === 0 ? null : ref / (zone.low / 100);
    const fast = ref / (zone.high / 100);
    return slow === null ? `< ${this.formatPace(fast)}` : `${this.formatPace(slow)}–${this.formatPace(fast)}`;
  }

  saveCustomRef(sys: ZoneSystem, user: User): void {
    const val = this.customRefInputs[sys.id!];
    if (val == null) return;
    this.authService.setCustomZoneReference(sys.id!, val).subscribe({
      next: () => this.authService.refreshUser(),
      error: () => {},
    });
  }

  getSectionTitle(): string {
    switch (this.activeSport) {
      case 'CYCLING': return 'COGGAN 7 ZONES — CYCLING';
      case 'RUNNING': return '5-ZONE SYSTEM — RUNNING';
      case 'SWIMMING': return '5-ZONE SYSTEM — SWIMMING';
    }
  }

  getRefBadge(user: User): string | null {
    switch (this.activeSport) {
      case 'CYCLING': return user.ftp ? `FTP: ${user.ftp}W` : null;
      case 'RUNNING': return user.functionalThresholdPace
        ? `Threshold: ${this.formatPace(user.functionalThresholdPace)}/km` : null;
      case 'SWIMMING': return user.criticalSwimSpeed
        ? `CSS: ${this.formatPace(user.criticalSwimSpeed)}/100m` : null;
    }
  }

  hasRef(user: User): boolean {
    switch (this.activeSport) {
      case 'CYCLING': return !!user.ftp;
      case 'RUNNING': return !!user.functionalThresholdPace;
      case 'SWIMMING': return !!user.criticalSwimSpeed;
    }
  }

  getEmptyNote(): string {
    switch (this.activeSport) {
      case 'CYCLING': return 'No FTP set. Update your reference values in Settings.';
      case 'RUNNING': return 'No threshold pace set. Update your reference values in Settings.';
      case 'SWIMMING': return 'No critical swim speed set. Update your reference values in Settings.';
    }
  }

  // Legacy methods kept for compatibility
  getPowerZones(ftp: number | undefined): { name: string; low: number; high: number | null; color: string }[] {
    if (!ftp) return [];
    return [
      { name: 'Z1 — Active Recovery', low: 0,                          high: Math.round(ftp * 0.55),       color: '#60a5fa' },
      { name: 'Z2 — Endurance',       low: Math.round(ftp * 0.55) + 1, high: Math.round(ftp * 0.75),       color: '#34d399' },
      { name: 'Z3 — Tempo',           low: Math.round(ftp * 0.75) + 1, high: Math.round(ftp * 0.90),       color: '#fbbf24' },
      { name: 'Z4 — Threshold',       low: Math.round(ftp * 0.90) + 1, high: Math.round(ftp * 1.05),       color: '#f97316' },
      { name: 'Z5 — VO2Max',          low: Math.round(ftp * 1.05) + 1, high: Math.round(ftp * 1.20),       color: '#f43f5e' },
      { name: 'Z6 — Anaerobic',       low: Math.round(ftp * 1.20) + 1, high: null,                         color: '#a855f7' },
    ];
  }

  getWkg(user: User): string {
    if (!user.ftp) return '—';
    const weight = user.weightKg ?? null;
    if (!weight) return '— (weight not set)';
    return (user.ftp / weight).toFixed(2);
  }

  getEstimatedVo2Max(user: User): string {
    if (!user.ftp) return '—';
    const weight = user.weightKg ?? 70;
    return ((user.ftp / 0.757) * (10.8 / weight)).toFixed(1);
  }

  getSportDistribution(sessions: any[]): { sport: string; count: number; pct: number }[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({ sport, count, pct: Math.round((count / sessions.length) * 100) }))
      .sort((a, b) => b.count - a.count);
  }
}
