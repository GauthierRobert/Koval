import {ChangeDetectionStrategy, Component, DestroyRef, inject, NgZone, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, map, Observable, of, take} from 'rxjs';
import {CoachService, ScheduledWorkout} from '../../../services/coach.service';
import {AuthService, User} from '../../../services/auth.service';
import {ClubService, MyClubRoleEntry} from '../../../services/club.service';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Group} from '../../../services/group.service';
import {MetricsService, PmcDataPoint} from '../../../services/metrics.service';
import {VolumeEntry} from '../../../services/analytics.service';
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
import {
  PMC_WINDOW_DAYS,
  PROJECTION_DAYS,
  VOLUME_WINDOW_DAYS,
  buildProjectionTssMap,
  dateOffsetKey,
  deriveAthleteMetrics,
  filterAthletes,
  formatScheduleWeekLabel,
  getMondayOfWeek,
  getSundayOfWeek,
  mapSessionToSavedSession,
  toDateKey,
} from './coach-dashboard.utils';

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
  multiSelectMode = false;
  multiSelectedIds = new Set<string>();
  bulkAssignAthletes: User[] = [];
  isScheduleModalOpen = false;
  isInviteCodeModalOpen = false;
  isShareModalOpen = false;
  trainingToShare: Training | null = null;
  activeTagFilter: string | null = null;
  activeTab: 'performance' | 'physiology' | 'history' | 'pmc' | 'goals' | 'plans' = 'performance';

  scheduleWeekStart: Date = getMondayOfWeek(new Date());
  scheduleWeekEnd: Date = getSundayOfWeek(new Date());

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
  ]).pipe(map(([athletes, tag, club]) => filterAthletes(athletes, tag, club)));

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
      ...this.metricsService.projectPmcFromSchedule(real, tssMap, PROJECTION_DAYS),
    ]),
  );

  private athleteGoalsSubject = new BehaviorSubject<RaceGoal[]>([]);
  athleteGoals$ = this.athleteGoalsSubject.asObservable();

  private athleteVolumeSubject = new BehaviorSubject<VolumeEntry[]>([]);
  athleteVolume$ = this.athleteVolumeSubject.asObservable();

  athleteMetrics$ = this.athletePmc$.pipe(map(deriveAthleteMetrics));

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
    this.trainingService.loadTrainings();
  }

  navigateScheduleWeek(dir: -1 | 1): void {
    this.scheduleWeekStart.setDate(this.scheduleWeekStart.getDate() + dir * 7);
    this.scheduleWeekEnd.setDate(this.scheduleWeekEnd.getDate() + dir * 7);
    this.scheduleWeekStart = new Date(this.scheduleWeekStart);
    this.scheduleWeekEnd = new Date(this.scheduleWeekEnd);
    if (this.selectedAthlete) this.loadAthleteSchedule(this.selectedAthlete.id);
  }

  get scheduleWeekLabel(): string {
    return formatScheduleWeekLabel(this.scheduleWeekStart, this.scheduleWeekEnd);
  }

  loadAthletes() {
    this.coachService.getAthletes().subscribe({
      next: (data) => this.ngZone.run(() => {
        this.athletesSubject.next(data);
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
    this.scheduleWeekStart = getMondayOfWeek(new Date());
    this.scheduleWeekEnd = getSundayOfWeek(new Date());
    this.athleteScheduleTssSubject.next(new Map());
    this.selectedAthleteSessionSubject.next(null);
    this.selectedAthlete = athlete;
    this.loadAthleteSchedule(athlete.id);
    this.loadAthleteSessions(athlete.id);
    this.loadAthletePmc(athlete.id);
    this.loadAthleteGoals(athlete.id);
    this.loadAthleteProjectionSchedule(athlete.id);
    this.loadAthleteVolume(athlete.id);
  }

  loadAthleteVolume(athleteId: string): void {
    this.loadAthleteData(
      this.coachService.getAthleteVolume(
        athleteId,
        dateOffsetKey(-VOLUME_WINDOW_DAYS),
        dateOffsetKey(0),
        'week',
      ),
      this.athleteVolumeSubject,
      []
    );
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
    this.loadAthleteData(
      this.coachService.getAthletePmc(
        athleteId,
        dateOffsetKey(-PMC_WINDOW_DAYS),
        dateOffsetKey(0),
      ),
      this.athletePmcSubject,
      []
    );
  }

  private loadAthleteProjectionSchedule(athleteId: string): void {
    this.coachService.getAthleteSchedule(athleteId, dateOffsetKey(0), dateOffsetKey(PROJECTION_DAYS)).subscribe({
      next: (workouts) => this.ngZone.run(
        () => this.athleteScheduleTssSubject.next(buildProjectionTssMap(workouts)),
      ),
      error: () => this.ngZone.run(() => this.athleteScheduleTssSubject.next(new Map())),
    });
  }

  viewAthletePmc(athleteId: string): void {
    this.router.navigate(['/pmc'], { queryParams: { athleteId } });
  }

  loadAthleteSchedule(athleteId: string) {
    const start = toDateKey(this.scheduleWeekStart);
    const end = toDateKey(this.scheduleWeekEnd);

    this.coachService.getAthleteSchedule(athleteId, start, end).subscribe({
      next: (data) => this.ngZone.run(() => this.scheduleSubject.next(data)),
      error: (err) => console.error('Error loading schedule', err)
    });
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
    this.coachTrainings$.pipe(take(1)).subscribe(trainings => {
      if (trainings.length > 0) {
        this.trainingToShare = trainings[0];
        this.isShareModalOpen = true;
      }
    });
  }

  onTrainingShared() {
    this.isShareModalOpen = false;
  }

  assignWorkout() {
    if (!this.selectedAthlete) return;
    this.bulkAssignAthletes = [];
    this.isScheduleModalOpen = true;
  }

  toggleMultiSelectMode(): void {
    this.multiSelectMode = !this.multiSelectMode;
    if (!this.multiSelectMode) {
      this.multiSelectedIds = new Set();
    }
  }

  toggleAthleteSelection(athlete: User): void {
    const next = new Set(this.multiSelectedIds);
    if (next.has(athlete.id)) {
      next.delete(athlete.id);
    } else {
      next.add(athlete.id);
    }
    this.multiSelectedIds = next;
  }

  bulkAssign(): void {
    if (this.multiSelectedIds.size === 0) return;
    const athletes = this.athletesSubject.value.filter(a => this.multiSelectedIds.has(a.id));
    if (athletes.length === 0) return;
    this.bulkAssignAthletes = athletes;
    this.isScheduleModalOpen = true;
  }

  onScheduled() {
    this.isScheduleModalOpen = false;
    if (this.bulkAssignAthletes.length > 0) {
      this.bulkAssignAthletes = [];
      this.multiSelectedIds = new Set();
      this.multiSelectMode = false;
    }
    if (this.selectedAthlete) {
      this.loadAthleteSchedule(this.selectedAthlete.id);
    }
  }

  openSessionAnalysis(session: SessionData): void {
    this.coachService.getSessionById(session.id).subscribe({
      next: (s: any) => this.ngZone.run(
        () => this.selectedAthleteSessionSubject.next(mapSessionToSavedSession(s)),
      ),
      error: () => {},
    });
  }

  closeSessionAnalysis(): void {
    this.selectedAthleteSessionSubject.next(null);
  }

  trackByValue(value: string): string { return value; }
}
