import {ChangeDetectionStrategy, Component, DestroyRef, inject, NgZone, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject, combineLatest, map, Observable, of} from 'rxjs';
import {CoachService, ScheduledWorkout} from '../../../services/coach.service';
import {AuthService, User} from '../../../services/auth.service';
import {ClubService, MyClubRoleEntry} from '../../../services/club.service';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Group} from '../../../services/group.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {MetricsService, PmcDataPoint} from '../../../services/metrics.service';
import {TrainingActionModalComponent} from '../../shared/training-action-modal/training-action-modal.component';
import {InviteCodeModalComponent} from '../../shared/invite-code-modal/invite-code-modal.component';
import {ShareTrainingModalComponent} from '../../shared/share-training-modal/share-training-modal.component';
import {TrainingService} from '../../../services/training.service';
import {Training, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType} from '../../../models/training.model';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {PmcChartComponent} from '../../shared/pmc-chart/pmc-chart.component';
import {daysUntil as sharedDaysUntil, formatPaceWithUnit, formatTimeHMS} from '../../shared/format/format.utils';

import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {SessionData, SessionSummary} from '../../../models/session-types.model';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TrainingActionModalComponent, InviteCodeModalComponent, ShareTrainingModalComponent, SportIconComponent, PmcChartComponent],
  templateUrl: './coach-dashboard.component.html',
  styleUrl: './coach-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachDashboardComponent implements OnInit {
  selectedAthlete: User | null = null;
  isScheduleModalOpen = false;
  isInviteCodeModalOpen = false;
  isShareModalOpen = false;
  trainingToShare: Training | null = null;
  activeTagFilter: string | null = null;
  activeTab: 'performance' | 'physiology' | 'history' | 'pmc' | 'goals' = 'performance';

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

  private groupsSubject = new BehaviorSubject<Group[]>([]);
  allTags$ = this.groupsSubject.asObservable();

  private tagFilterSubject = new BehaviorSubject<string | null>(null);

  private clubFilterSubject = new BehaviorSubject<string | null>(null);
  activeClubFilter: string | null = null;

  private clubRolesSubject = new BehaviorSubject<MyClubRoleEntry[]>([]);
  clubRoles$ = this.clubRolesSubject.asObservable();

  filteredAthletes$: Observable<User[]> = combineLatest([
    this.athletes$,
    this.tagFilterSubject,
    this.clubFilterSubject,
  ]).pipe(
    map(([athletes, tagFilter, clubFilter]) => {
      let result = athletes;
      if (tagFilter) result = result.filter(a => a.groups?.includes(tagFilter));
      if (clubFilter) result = result.filter(a => a.clubs?.includes(clubFilter));
      return result;
    })
  );

  private scheduleSubject = new BehaviorSubject<ScheduledWorkout[]>([]);
  athleteSchedule$ = this.scheduleSubject.asObservable();

  private athleteSessionsSubject = new BehaviorSubject<SessionData[]>([]);
  athleteSessions$ = this.athleteSessionsSubject.asObservable();

  private athleteSessionsErrorSubject = new BehaviorSubject<boolean>(false);
  athleteSessionsError$ = this.athleteSessionsErrorSubject.asObservable();

  private athletePmcSubject = new BehaviorSubject<PmcDataPoint[]>([]);
  athletePmc$ = this.athletePmcSubject.asObservable();

  private athleteScheduleTssSubject = new BehaviorSubject<Map<string, number>>(new Map());

  fullAthletePmc$ = combineLatest([this.athletePmc$, this.athleteScheduleTssSubject]).pipe(
    map(([real, tssMap]) => [
      ...real,
      ...this.metricsService.projectPmcFromSchedule(real, tssMap, 30),
    ]),
  );

  private athleteGoalsSubject = new BehaviorSubject<RaceGoal[]>([]);
  athleteGoals$ = this.athleteGoalsSubject.asObservable();

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

  private readonly coachService = inject(CoachService);
  private readonly authService = inject(AuthService);
  private readonly clubService = inject(ClubService);
  private readonly metricsService = inject(MetricsService);
  private readonly trainingService = inject(TrainingService);
  private readonly zoneService = inject(ZoneService);
  private readonly raceGoalService = inject(RaceGoalService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly ngZone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
      if (u) {
        this.userId = u.id;
        this.loadAthletes();
        this.loadTags();
        this.zoneService.getCoachZoneSystems().subscribe({
          next: (systems) => this.ngZone.run(() => this.coachZoneSystemsSubject.next(systems)),
          error: () => {}
        });
        this.clubService.loadMyClubRoles();
        this.clubService.myClubRoles$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(
          roles => this.clubRolesSubject.next(roles.filter(r => r.role !== 'MEMBER'))
        );
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
    this.coachService.getAllGroups().subscribe({
      next: (groups) => this.ngZone.run(() => this.groupsSubject.next(groups)),
      error: (err) => console.error('Error loading groups', err)
    });
  }

  getTagCount(tag: string): number {
    return this.athletesSubject.value.filter(a => a.groups?.includes(tag)).length;
  }

  setTagFilter(tag: string | null) {
    this.activeTagFilter = tag;
    this.tagFilterSubject.next(tag);
  }

  toggleTagFilter(tag: string) {
    this.setTagFilter(this.activeTagFilter === tag ? null : tag);
  }

  setClubFilter(clubName: string | null): void {
    this.activeClubFilter = clubName;
    this.clubFilterSubject.next(clubName);
  }

  toggleClubFilter(clubName: string): void {
    this.setClubFilter(this.activeClubFilter === clubName ? null : clubName);
  }

  getClubCount(clubName: string): number {
    return this.athletesSubject.value.filter(a => a.clubs?.includes(clubName)).length;
  }

  selectAthlete(athlete: User) {
    this.scheduleWeekStart = this.getMondayOfWeek(new Date());
    this.scheduleWeekEnd = this.getSundayOfWeek(new Date());
    this.athleteScheduleTssSubject.next(new Map());
    this.selectedAthlete = athlete;
    this.loadAthleteSchedule(athlete.id);
    this.loadAthleteSessions(athlete.id);
    this.loadAthletePmc(athlete.id);
    this.loadAthleteGoals(athlete.id);
    this.loadAthleteProjectionSchedule(athlete.id);
  }

  private loadAthleteData<T>(
    loader: Observable<T>,
    subject: BehaviorSubject<T>,
    defaultValue: T
  ): void {
    loader.subscribe({
      next: (data) => this.ngZone.run(() => subject.next(data)),
      error: () => this.ngZone.run(() => subject.next(defaultValue)),
    });
  }

  loadAthleteGoals(athleteId: string): void {
    this.loadAthleteData(
      this.raceGoalService.getAthleteGoals(athleteId),
      this.athleteGoalsSubject,
      []
    );
  }

  // Task 3: Wrap athleteSessionsSubject.next() in ngZone.run()
  loadAthleteSessions(athleteId: string): void {
    this.athleteSessionsErrorSubject.next(false);
    this.coachService.getAthleteSessions(athleteId).subscribe({
      next: (sessions: SessionData[]) => this.ngZone.run(() => {
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
    const from = new Date(now); from.setDate(from.getDate() - 90);
    const to = new Date(now); to.setDate(to.getDate() + 30);
    this.loadAthleteData(
      this.coachService.getAthletePmc(
        athleteId,
        from.toISOString().split('T')[0],
        to.toISOString().split('T')[0]
      ),
      this.athletePmcSubject,
      []
    );
  }

  private loadAthleteProjectionSchedule(athleteId: string): void {
    const today = new Date().toISOString().split('T')[0];
    const future = new Date();
    future.setDate(future.getDate() + 30);
    const futureStr = future.toISOString().split('T')[0];
    this.coachService.getAthleteSchedule(athleteId, today, futureStr).subscribe({
      next: (workouts) => {
        const m = new Map<string, number>();
        for (const w of workouts) {
          if (w.status === 'PENDING' && w.tss) {
            m.set(w.scheduledDate, (m.get(w.scheduledDate) ?? 0) + w.tss);
          }
        }
        this.ngZone.run(() => this.athleteScheduleTssSubject.next(m));
      },
      error: () => this.ngZone.run(() => this.athleteScheduleTssSubject.next(new Map())),
    });
  }

  viewAthletePmc(athleteId: string): void {
    this.router.navigate(['/pmc'], { queryParams: { athleteId } });
  }

  loadAthleteSchedule(athleteId: string) {
    const start = this.toDateKey(this.scheduleWeekStart);
    const end = this.toDateKey(this.scheduleWeekEnd);

    this.coachService.getAthleteSchedule(athleteId, start, end).subscribe({
      next: (data) => this.ngZone.run(() => this.scheduleSubject.next(data)),
      error: (err) => console.error('Error loading schedule', err)
    });
  }

  private toDateKey(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
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
    this.coachService.removeAthleteGroup(athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.groups = updated.groups;
        this.loadTags();
      },
      error: () => {
        if (athlete.groups) {
          athlete.groups = athlete.groups.filter(t => t !== tag);
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
    return formatPaceWithUnit(secPerKm, 'RUNNING');
  }

  formatSwimPace(secPer100m: number | null): string {
    return formatPaceWithUnit(secPer100m, 'SWIMMING');
  }

  getEstimatedVo2Max(ftp: number | undefined): string {
    if (!ftp) return '—';
    return ((ftp / 0.757) * (10.8 / 70)).toFixed(1);
  }

  getSportDistribution(sessions: SessionData[]): SessionSummary[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({ sport, count, pct: Math.round(count / sessions.length * 100) }))
      .sort((a, b) => b.count - a.count);
  }

  formatSessionDur(sec: number): string {
    return formatTimeHMS(sec);
  }

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  // Task 7: Form condition label
  getFormCondition(tsb: number): string {
    if (tsb > 5) return 'FRESH';
    if (tsb < -10) return 'TIRED';
    return 'NEUTRAL';
  }

  trackTagByName(group: Group): string { return group.name; }
  trackAthleteById(athlete: User): string { return athlete.id; }
  trackByValue(value: string): string { return value; }
  trackScheduleById(workout: ScheduledWorkout): string { return workout.id; }
  trackSessionById(s: SessionData): string { return s.id; }
  trackZoneByName(z: { name: string }): string { return z.name; }
  trackZoneByLabel(z: { label: string }): string { return z.label; }
  trackDistBySport(d: { sport: string }): string { return d.sport; }
  trackSystemByName(sys: { name: string }): string { return sys.name; }
  trackGoalById(goal: RaceGoal): string { return goal.id; }
}
