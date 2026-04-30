import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Observable} from 'rxjs';
import {User} from '../../../../services/auth.service';
import {ScheduledWorkout} from '../../../../services/coach.service';
import {Training, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType} from '../../../../models/training.model';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {SessionData, SessionSummary} from '../../../../models/session-types.model';
import {formatTimeHMS} from '../../../shared/format/format.utils';
import {VolumeEntry} from '../../../../services/analytics.service';
import {DashboardVolumeChartComponent} from '../../dashboard/dashboard-volume-chart/dashboard-volume-chart.component';

@Component({
  selector: 'app-coach-performance-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent, DashboardVolumeChartComponent],
  templateUrl: './coach-performance-tab.component.html',
  styleUrl: './coach-performance-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachPerformanceTabComponent {
  @Input() athleteMetrics$!: Observable<{
    ctl: number;
    atl: number;
    tsb: number;
    ctlTrend: number;
    atlTrend: number;
  } | null>;
  @Input() athleteSchedule$!: Observable<ScheduledWorkout[]>;
  @Input() athleteSessions$!: Observable<SessionData[]>;
  @Input() athleteVolume$!: Observable<VolumeEntry[]>;
  @Input() scheduleWeekStart!: Date;
  @Input() scheduleWeekEnd!: Date;
  @Input() scheduleWeekLabel = '';
  @Input() selectedAthlete!: User;
  @Input() coachTrainings$!: Observable<Training[]>;

  @Output() navigateWeek = new EventEmitter<-1 | 1>();
  @Output() openSessionAnalysis = new EventEmitter<SessionData>();
  @Output() assignWorkout = new EventEmitter<void>();

  volumeMetric: 'time' | 'tss' | 'distance' = 'time';

  private readonly translate = inject(TranslateService);

  getWorkoutTitle(workout: ScheduledWorkout): string {
    return workout.trainingTitle || workout.title || 'W-' + workout.trainingId.substring(0, 8);
  }

  getWorkoutDuration(workout: ScheduledWorkout): string {
    if (workout.totalDurationSeconds) {
      const totalSec = workout.totalDurationSeconds;
      const h = Math.floor(totalSec / 3600);
      const m = Math.floor((totalSec % 3600) / 60);
      const s = totalSec % 60;
      return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    }
    return workout.duration || '-';
  }

  formatSessionDur(sec: number): string {
    return formatTimeHMS(sec);
  }

  getSportDistribution(sessions: SessionData[]): SessionSummary[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({sport, count, pct: Math.round(count / sessions.length * 100)}))
      .sort((a, b) => b.count - a.count);
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  getFormCondition(tsb: number): string {
    if (tsb > 5) return this.translate.instant('COACH_DASHBOARD.CONDITION_FRESH');
    if (tsb < -10) return this.translate.instant('COACH_DASHBOARD.CONDITION_TIRED');
    return this.translate.instant('COACH_DASHBOARD.CONDITION_NEUTRAL');
  }

  toDateKey(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  trackScheduleById(workout: ScheduledWorkout): string { return workout.id; }
  trackSessionById(s: SessionData): string { return s.id; }
  trackDistBySport(d: { sport: string }): string { return d.sport; }
}
