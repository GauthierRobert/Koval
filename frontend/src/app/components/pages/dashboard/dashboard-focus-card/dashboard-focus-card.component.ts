import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output, computed, inject, signal} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';
import {ScheduledWorkout} from '../../../../services/coach.service';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {TrainingService} from '../../../../services/training.service';
import {ZoneClassificationService} from '../../../../services/zone-classification.service';
import {DurationEstimationService} from '../../../../services/duration-estimation.service';
import {Training, WorkoutBlock, flattenElements} from '../../../../models/training.model';
import {normalizeSport} from '../../../shared/block-helpers/block-helpers';

interface ZoneSlice {
  label: string;
  color: string;
  pct: number;
  seconds: number;
}

@Component({
  selector: 'app-dashboard-focus-card',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './dashboard-focus-card.component.html',
  styleUrl: './dashboard-focus-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardFocusCardComponent {
  private readonly trainingService = inject(TrainingService);
  private readonly zoneCls = inject(ZoneClassificationService);
  private readonly durationService = inject(DurationEstimationService);

  private readonly _upcoming = signal<ScheduledWorkout[]>([]);
  private readonly _today = signal<string>('');
  private readonly _ftp = signal<number | null>(null);

  @Input() userName = 'Athlete';
  @Input() todayDate: Date = new Date();

  @Input()
  set upcomingWorkouts(v: ScheduledWorkout[]) { this._upcoming.set(v ?? []); }
  get upcomingWorkouts() { return this._upcoming(); }

  @Input()
  set todayKey(v: string) { this._today.set(v ?? ''); }
  get todayKey() { return this._today(); }

  @Input()
  set ftp(v: number | null) { this._ftp.set(v); }
  get ftp() { return this._ftp(); }

  @Output() openDetail = new EventEmitter<ScheduledWorkout>();
  @Output() startNow = new EventEmitter<ScheduledWorkout>();

  readonly todaysWorkout = computed<ScheduledWorkout | null>(() => {
    const list = this._upcoming();
    const key = this._today();
    return list.find((w) => w.scheduledDate === key) ?? null;
  });

  private readonly training = toSignal(
    toObservable(this.todaysWorkout).pipe(
      switchMap((w) => {
        if (!w?.trainingId) return of(null as Training | null);
        return this.trainingService.getTrainingById(w.trainingId).pipe(catchError(() => of(null as Training | null)));
      }),
    ),
    {initialValue: null as Training | null},
  );

  readonly zoneDistribution = computed<ZoneSlice[]>(() => {
    const t = this.training();
    if (!t?.blocks?.length) return [];
    const sport = normalizeSport(t.sportType);
    const zones = this.zoneCls.defaultZonesBySport[sport];
    const zoneSeconds = new Array(zones.length).fill(0);
    let total = 0;

    for (const block of flattenElements(t.blocks)) {
      if (block.type === 'PAUSE' || block.type === 'TRANSITION' || block.type === 'FREE') continue;
      const intensity = this.blockIntensity(block);
      if (intensity == null) continue;
      const duration = this.durationService.estimateDuration(block, t, null);
      if (duration <= 0) continue;
      const zi = this.zoneCls.classifyZone(intensity, zones);
      zoneSeconds[zi] += duration;
      total += duration;
    }

    if (total === 0) return [];

    return zones.map((z, i) => ({
      label: z.label,
      color: this.zoneCls.getZoneColor(i, zones, sport),
      pct: Math.round((zoneSeconds[i] / total) * 100),
      seconds: zoneSeconds[i],
    }));
  });

  readonly weekMeta = computed(() => {
    const date = this.todayDate;
    const start = new Date(date.getFullYear(), 0, 1);
    const diff = (date.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
    return Math.floor(diff / 7) + 1;
  });

  readonly intensityFactor = computed(() => {
    const w = this.todaysWorkout();
    if (!w?.totalDurationSeconds || !w.totalDurationSeconds) return null;
    const tss = (w as unknown as { tss?: number }).tss;
    if (!tss) return null;
    const durationHours = w.totalDurationSeconds / 3600;
    if (durationHours <= 0) return null;
    const value = Math.sqrt((tss / 100) / durationHours);
    return Math.round(value * 100) / 100;
  });

  readonly workoutTss = computed(() => {
    const w = this.todaysWorkout();
    return (w as unknown as { tss?: number } | null)?.tss ?? null;
  });

  readonly hrTarget = computed(() => {
    const w = this.todaysWorkout();
    if (!w) return null;
    const anyW = w as unknown as { hrMin?: number; hrMax?: number };
    if (anyW.hrMin && anyW.hrMax) return { min: anyW.hrMin, max: anyW.hrMax };
    return null;
  });

  readonly powerTarget = computed(() => {
    const w = this.todaysWorkout();
    if (!w) return null;
    const anyW = w as unknown as { powerMinWatts?: number; powerMaxWatts?: number; powerTargetPercent?: number };
    if (anyW.powerMinWatts && anyW.powerMaxWatts) return { min: anyW.powerMinWatts, max: anyW.powerMaxWatts };
    const ftp = this._ftp();
    const pct = anyW.powerTargetPercent;
    if (ftp && pct) {
      const min = Math.round(ftp * (pct - 5) / 100);
      const max = Math.round(ftp * (pct + 5) / 100);
      return { min, max };
    }
    return null;
  });

  readonly sportAccent = computed(() => {
    const sport = this.todaysWorkout()?.sportType;
    return this.sportColor(sport);
  });

  formatScheduleDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '';
    const totalSec = workout.totalDurationSeconds;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = Math.floor(totalSec % 60);
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
  }

  formatDistance(workout: ScheduledWorkout): string | null {
    const anyW = workout as unknown as { distanceMeters?: number };
    if (!anyW.distanceMeters || anyW.distanceMeters <= 0) return null;
    if (workout.sportType === 'SWIMMING') return `${Math.round(anyW.distanceMeters)} m`;
    return `${(anyW.distanceMeters / 1000).toFixed(1)} km`;
  }

  sportLabel(sport?: string): string {
    if (!sport) return '';
    return sport.charAt(0) + sport.slice(1).toLowerCase();
  }

  private sportColor(sport?: string): string {
    switch (sport) {
      case 'CYCLING': return '#34d399';
      case 'RUNNING': return '#f87171';
      case 'SWIMMING': return '#00a0e9';
      case 'BRICK':   return '#ff9d00';
      case 'GYM':     return '#a78bfa';
      default:        return 'var(--accent-color)';
    }
  }

  private blockIntensity(block: WorkoutBlock): number | null {
    if (block.type === 'RAMP') {
      const s = block.intensityStart;
      const e = block.intensityEnd;
      if (s == null && e == null) return null;
      return ((s ?? 0) + (e ?? 0)) / 2;
    }
    return block.intensityTarget ?? null;
  }
}
