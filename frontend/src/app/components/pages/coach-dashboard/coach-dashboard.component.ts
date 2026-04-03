import {ChangeDetectionStrategy, Component, DestroyRef, inject, NgZone, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, map, Observable, of} from 'rxjs';
import {CoachService, ScheduledWorkout} from '../../../services/coach.service';
import {AuthService, User} from '../../../services/auth.service';
import {ClubService, MyClubRoleEntry} from '../../../services/club.service';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Group} from '../../../services/group.service';
import {MetricsService, PmcDataPoint} from '../../../services/metrics.service';
import {TrainingActionModalComponent} from '../../shared/training-action-modal/training-action-modal.component';
import {InviteCodeModalComponent} from '../../shared/invite-code-modal/invite-code-modal.component';
import {ShareTrainingModalComponent} from '../../shared/share-training-modal/share-training-modal.component';
import {TrainingService} from '../../../services/training.service';
import {Training} from '../../../models/training.model';
import {PmcChartComponent} from '../../shared/pmc-chart/pmc-chart.component';
import {SavedSession} from '../../../services/history.service';

import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {SessionData} from '../../../models/session-types.model';
import {PhysiologyPageComponent} from '../physiology-page/physiology-page.component';
import {AthleteListSidebarComponent} from './athlete-list-sidebar/athlete-list-sidebar.component';
import {CoachPerformanceTabComponent} from './coach-performance-tab/coach-performance-tab.component';
import {CoachGoalsTabComponent} from './coach-goals-tab/coach-goals-tab.component';
import {CoachHistoryTabComponent} from './coach-history-tab/coach-history-tab.component';
import {CoachPlansTabComponent} from './coach-plans-tab/coach-plans-tab.component';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, TrainingActionModalComponent, InviteCodeModalComponent, ShareTrainingModalComponent, PmcChartComponent, PhysiologyPageComponent, AthleteListSidebarComponent, CoachPerformanceTabComponent, CoachGoalsTabComponent, CoachHistoryTabComponent, CoachPlansTabComponent],
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
  activeTab: 'performance' | 'physiology' | 'history' | 'pmc' | 'goals' | 'plans' = 'performance';

  scheduleWeekStart: Date = this.getMondayOfWeek(new Date());
  scheduleWeekEnd: Date = this.getSundayOfWeek(new Date());

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

  private selectedAthleteSessionSubject = new BehaviorSubject<SavedSession | null>(null);
  selectedAthleteSession$ = this.selectedAthleteSessionSubject.asObservable();

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

  clearFilters(): void {
    this.setTagFilter(null);
    this.setClubFilter(null);
  }

  selectAthlete(athlete: User) {
    this.scheduleWeekStart = this.getMondayOfWeek(new Date());
    this.scheduleWeekEnd = this.getSundayOfWeek(new Date());
    this.athleteScheduleTssSubject.next(new Map());
    this.selectedAthleteSessionSubject.next(null);
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
    this.loadAthleteData(
      this.coachService.getAthletePmc(
        athleteId,
        from.toISOString().split('T')[0],
        now.toISOString().split('T')[0]
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

  openSessionAnalysis(session: SessionData): void {
    this.coachService.getSessionById(session.id).subscribe({
      next: (s: any) => this.ngZone.run(() => {
        const saved: SavedSession = {
          id: s.id,
          title: s.title,
          totalDuration: s.totalDurationSeconds,
          avgPower: s.avgPower,
          avgHR: s.avgHR,
          avgCadence: s.avgCadence,
          avgSpeed: s.avgSpeed || 0,
          blockSummaries: s.blockSummaries || [],
          history: [],
          sportType: s.sportType,
          date: new Date(s.completedAt),
          syncedToStrava: false,
          syncedToGarmin: false,
          tss: s.tss ?? undefined,
          intensityFactor: s.intensityFactor ?? undefined,
          fitFileId: s.fitFileId ?? undefined,
          stravaActivityId: s.stravaActivityId ?? undefined,
        };
        this.selectedAthleteSessionSubject.next(saved);
      }),
      error: () => {},
    });
  }

  closeSessionAnalysis(): void {
    this.selectedAthleteSessionSubject.next(null);
  }

  trackByValue(value: string): string { return value; }
}
