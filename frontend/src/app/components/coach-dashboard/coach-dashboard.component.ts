import { Component, NgZone, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, Observable, of, map } from 'rxjs';
import { CoachService, ScheduledWorkout } from '../../services/coach.service';
import { AuthService, User } from '../../services/auth.service';
import { Tag } from '../../services/tag.service';
import { ZoneService } from '../../services/zone.service';
import { ZoneSystem } from '../../services/zone';
import { PmcDataPoint } from '../../services/metrics.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { InviteCodeModalComponent } from '../invite-code-modal/invite-code-modal.component';
import { ShareTrainingModalComponent } from '../share-training-modal/share-training-modal.component';
import { Training, TrainingService, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { PmcChartComponent } from '../pmc-chart/pmc-chart.component';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ScheduleModalComponent, InviteCodeModalComponent, ShareTrainingModalComponent, SportIconComponent, PmcChartComponent],
  templateUrl: './coach-dashboard.component.html',
  styleUrl: './coach-dashboard.component.css',
})
export class CoachDashboardComponent implements OnInit {
  selectedAthlete: User | null = null;
  isScheduleModalOpen = false;
  isInviteCodeModalOpen = false;
  isShareModalOpen = false;
  trainingToShare: Training | null = null;
  activeTagFilter: string | null = null;
  activeTab: 'performance' | 'physiology' | 'history' | 'pmc' = 'performance';

  scheduleWeekStart: Date = this.getMondayOfWeek(new Date());
  scheduleWeekEnd: Date = this.getSundayOfWeek(new Date());

  private coachZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  coachZoneSystems$ = this.coachZoneSystemsSubject.asObservable();

  readonly ZONE_COLORS = ['#6366f1', '#3b82f6', '#22c55e', '#eab308', '#f97316', '#ef4444', '#7f1d1d'];
  getZoneColor(i: number): string { return this.ZONE_COLORS[i % this.ZONE_COLORS.length]; }

  private userId = '';

  // Reactive state
  private athletesSubject = new BehaviorSubject<User[]>([]);
  athletes$ = this.athletesSubject.asObservable();

  private tagsSubject = new BehaviorSubject<Tag[]>([]);
  allTags$ = this.tagsSubject.asObservable();

  private tagFilterSubject = new BehaviorSubject<string | null>(null);

  filteredAthletes$: Observable<User[]> = combineLatest([
    this.athletes$,
    this.tagFilterSubject,
  ]).pipe(
    map(([athletes, filter]) => filter ? athletes.filter(a => a.tags?.includes(filter)) : athletes)
  );

  private scheduleSubject = new BehaviorSubject<ScheduledWorkout[]>([]);
  athleteSchedule$ = this.scheduleSubject.asObservable();

  private athleteSessionsSubject = new BehaviorSubject<any[]>([]);
  athleteSessions$ = this.athleteSessionsSubject.asObservable();

  private athleteSessionsErrorSubject = new BehaviorSubject<boolean>(false);
  athleteSessionsError$ = this.athleteSessionsErrorSubject.asObservable();

  private athletePmcSubject = new BehaviorSubject<PmcDataPoint[]>([]);
  athletePmc$ = this.athletePmcSubject.asObservable();

  // Task 7: Real fitness/fatigue/form metrics derived from PMC data
  athleteMetrics$ = this.athletePmc$.pipe(
    map(data => {
      if (!data.length) return null;
      const real = data.filter(d => !d.predicted);
      if (!real.length) return null;
      const latest = real[real.length - 1];
      const tenDaysAgo = real.length > 10 ? real[real.length - 11] : null;
      return {
        ctl: latest?.ctl ?? 0,
        atl: latest?.atl ?? 0,
        tsb: latest?.tsb ?? 0,
        ctlTrend: tenDaysAgo ? latest.ctl - tenDaysAgo.ctl : 0,
        atlTrend: tenDaysAgo ? latest.atl - tenDaysAgo.atl : 0,
      };
    })
  );

  coachTrainings$: Observable<Training[]> = of([]);

  constructor(
    private coachService: CoachService,
    private authService: AuthService,
    private trainingService: TrainingService,
    private zoneService: ZoneService,
    private router: Router,
    private route: ActivatedRoute,
    private ngZone: NgZone
  ) { }

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      if (u) {
        this.userId = u.id;
        this.loadAthletes();
        this.loadTags();
        this.zoneService.getCoachZoneSystems().subscribe({
          next: (systems) => this.ngZone.run(() => this.coachZoneSystemsSubject.next(systems)),
          error: () => {}
        });
      }
    });
    this.coachTrainings$ = this.trainingService.trainings$;
  }

  private getMondayOfWeek(d: Date): Date {
    const day = d.getDay();
    const offset = day === 0 ? -6 : 1 - day;
    const m = new Date(d);
    m.setDate(d.getDate() + offset);
    m.setHours(0, 0, 0, 0);
    return m;
  }

  private getSundayOfWeek(d: Date): Date {
    const mon = this.getMondayOfWeek(d);
    const sun = new Date(mon);
    sun.setDate(mon.getDate() + 6);
    return sun;
  }

  navigateScheduleWeek(dir: -1 | 1): void {
    this.scheduleWeekStart.setDate(this.scheduleWeekStart.getDate() + dir * 7);
    this.scheduleWeekEnd.setDate(this.scheduleWeekEnd.getDate() + dir * 7);
    this.scheduleWeekStart = new Date(this.scheduleWeekStart);
    this.scheduleWeekEnd = new Date(this.scheduleWeekEnd);
    if (this.selectedAthlete) this.loadAthleteSchedule(this.selectedAthlete.id);
  }

  get scheduleWeekLabel(): string {
    const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${this.scheduleWeekStart.toLocaleDateString('en-US', opts)} – ${this.scheduleWeekEnd.toLocaleDateString('en-US', opts)}`;
  }

  loadAthletes() {
    this.coachService.getAthletes().subscribe({
      next: (data) => this.ngZone.run(() => {
        this.athletesSubject.next(data);
        // Task 6: Auto-select athlete from query params after athletes load
        const athleteId = this.route.snapshot.queryParamMap.get('athleteId');
        if (athleteId) {
          const athlete = data.find(a => a.id === athleteId);
          if (athlete) this.selectAthlete(athlete);
        }
      }),
      error: (err) => console.error('Error loading athletes', err)
    });
  }

  loadTags() {
    this.coachService.getAllTags().subscribe({
      next: (tags) => this.ngZone.run(() => this.tagsSubject.next(tags)),
      error: (err) => console.error('Error loading tags', err)
    });
  }

  getTagCount(tag: string): number {
    return this.athletesSubject.value.filter(a => a.tags?.includes(tag)).length;
  }

  setTagFilter(tag: string | null) {
    this.activeTagFilter = tag;
    this.tagFilterSubject.next(tag);
  }

  toggleTagFilter(tag: string) {
    this.setTagFilter(this.activeTagFilter === tag ? null : tag);
  }

  selectAthlete(athlete: User) {
    this.scheduleWeekStart = this.getMondayOfWeek(new Date());
    this.scheduleWeekEnd = this.getSundayOfWeek(new Date());
    this.selectedAthlete = athlete;
    this.loadAthleteSchedule(athlete.id);
    this.loadAthleteSessions(athlete.id);
    this.loadAthletePmc(athlete.id);
  }

  // Task 3: Wrap athleteSessionsSubject.next() in ngZone.run()
  loadAthleteSessions(athleteId: string): void {
    this.athleteSessionsErrorSubject.next(false);
    this.coachService.getAthleteSessions(athleteId).subscribe({
      next: (sessions: any[]) => this.ngZone.run(() => {
        this.athleteSessionsSubject.next(sessions);
        this.athleteSessionsErrorSubject.next(false);
      }),
      error: () => this.ngZone.run(() => {
        this.athleteSessionsSubject.next([]);
        this.athleteSessionsErrorSubject.next(true);
      }),
    });
  }

  loadAthletePmc(athleteId: string): void {
    const now = new Date();
    const from = new Date(now); from.setDate(from.getDate() - 30);
    const to = new Date(now); to.setDate(to.getDate() + 30);
    this.coachService.getAthletePmc(
      athleteId,
      from.toISOString().split('T')[0],
      to.toISOString().split('T')[0]
    ).subscribe({
      next: (data) => this.ngZone.run(() => this.athletePmcSubject.next(data)),
      error: () => this.ngZone.run(() => this.athletePmcSubject.next([])),
    });
  }

  viewAthletePmc(athleteId: string): void {
    this.router.navigate(['/pmc'], { queryParams: { athleteId } });
  }

  loadAthleteSchedule(athleteId: string) {
    const start = this.scheduleWeekStart.toISOString().split('T')[0];
    const end = this.scheduleWeekEnd.toISOString().split('T')[0];

    this.coachService.getAthleteSchedule(athleteId, start, end).subscribe({
      next: (data) => this.ngZone.run(() => this.scheduleSubject.next(data)),
      error: (err) => console.error('Error loading schedule', err)
    });
  }

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

  removeTag(athlete: User | null, tag: string) {
    if (!athlete) return;
    this.coachService.removeAthleteTag(athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.tags = updated.tags;
        this.loadTags();
      },
      error: () => {
        if (athlete.tags) {
          athlete.tags = athlete.tags.filter(t => t !== tag);
        }
      }
    });
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  addAthlete() {
    this.isInviteCodeModalOpen = true;
  }

  onCodeGenerated() {
    this.loadTags();
  }

  openShareModal() {
    this.coachTrainings$.subscribe(trainings => {
      if (trainings.length > 0) {
        this.trainingToShare = trainings[0];
        this.isShareModalOpen = true;
      }
    }).unsubscribe;
  }

  onTrainingShared() {
    this.isShareModalOpen = false;
  }

  assignWorkout() {
    if (!this.selectedAthlete) return;
    this.isScheduleModalOpen = true;
  }

  onScheduled() {
    this.isScheduleModalOpen = false;
    if (this.selectedAthlete) {
      this.loadAthleteSchedule(this.selectedAthlete.id);
    }
  }

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

  // Task 1: Run zones (Coggan) — pace in sec/km derived from functionalThresholdPace
  getRunZones(ftp: number | undefined, thresholdPace: number | undefined): { name: string; low: number | null; high: number | null; color: string }[] {
    if (!thresholdPace) return [];
    // thresholdPace is in sec/km at threshold. Lower = faster.
    // Zone boundaries as percentage of threshold pace (inverse relationship — higher % means slower)
    return [
      { name: 'Z1 — Recovery',   low: Math.round(thresholdPace * 1.29), high: null,                              color: '#60a5fa' },
      { name: 'Z2 — Endurance',  low: Math.round(thresholdPace * 1.14), high: Math.round(thresholdPace * 1.29),  color: '#34d399' },
      { name: 'Z3 — Tempo',      low: Math.round(thresholdPace * 1.06), high: Math.round(thresholdPace * 1.14),  color: '#fbbf24' },
      { name: 'Z4 — Threshold',  low: Math.round(thresholdPace * 0.99), high: Math.round(thresholdPace * 1.06),  color: '#f97316' },
      { name: 'Z5 — VO2Max',     low: null,                             high: Math.round(thresholdPace * 0.99),  color: '#f43f5e' },
    ];
  }

  // Task 1: Swim zones derived from critical swim speed (sec/100m)
  getSwimZones(css: number | undefined): { name: string; low: number | null; high: number | null; color: string }[] {
    if (!css) return [];
    return [
      { name: 'Z1 — Recovery',   low: Math.round(css * 1.30), high: null,                          color: '#60a5fa' },
      { name: 'Z2 — Endurance',  low: Math.round(css * 1.15), high: Math.round(css * 1.30),        color: '#34d399' },
      { name: 'Z3 — Tempo',      low: Math.round(css * 1.05), high: Math.round(css * 1.15),        color: '#fbbf24' },
      { name: 'Z4 — Threshold',  low: Math.round(css * 0.97), high: Math.round(css * 1.05),        color: '#f97316' },
      { name: 'Z5 — VO2Max',     low: null,                   high: Math.round(css * 0.97),        color: '#f43f5e' },
    ];
  }

  // Task 1: Compute actual watts/pace from athlete ref value + zone %
  getCustomZoneActualRange(zone: { low: number; high: number }, athlete: User): string {
    const refValue = athlete.ftp || 0;
    if (!refValue) return `${zone.low}–${zone.high}%`;
    const low = Math.round(refValue * zone.low / 100);
    const high = Math.round(refValue * zone.high / 100);
    return `${low}–${high}W`;
  }

  formatPace(secPerKm: number | null): string {
    if (secPerKm === null) return '∞';
    const min = Math.floor(secPerKm / 60);
    const sec = secPerKm % 60;
    return `${min}:${String(sec).padStart(2, '0')}/km`;
  }

  formatSwimPace(secPer100m: number | null): string {
    if (secPer100m === null) return '∞';
    const min = Math.floor(secPer100m / 60);
    const sec = secPer100m % 60;
    return `${min}:${String(sec).padStart(2, '0')}/100m`;
  }

  getEstimatedVo2Max(ftp: number | undefined): string {
    if (!ftp) return '—';
    return ((ftp / 0.757) * (10.8 / 70)).toFixed(1);
  }

  getSportDistribution(sessions: any[]): { sport: string; count: number; pct: number }[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({ sport, count, pct: Math.round(count / sessions.length * 100) }))
      .sort((a, b) => b.count - a.count);
  }

  formatSessionDur(sec: number): string {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  // Task 7: Form condition label
  getFormCondition(tsb: number): string {
    if (tsb > 5) return 'FRESH';
    if (tsb < -10) return 'TIRED';
    return 'NEUTRAL';
  }
}
