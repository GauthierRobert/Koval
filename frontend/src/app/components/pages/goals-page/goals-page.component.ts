import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {BehaviorSubject, debounceTime, distinctUntilChanged, of, switchMap} from 'rxjs';
import {map, take} from 'rxjs/operators';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Race, RaceService, RouteCoordinate, SimulationRequest} from '../../../services/race.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {RouteMapComponent} from '../pacing/route-map/route-map.component';
import {daysUntil as sharedDaysUntil, weeksUntil as sharedWeeksUntil} from '../../shared/format/format.utils';
import {GoalSidebarItemComponent} from './goal-sidebar-item/goal-sidebar-item.component';
import {GoalCountdownHeroComponent} from './goal-countdown-hero/goal-countdown-hero.component';
import {GpxDisciplineUploaderComponent} from './gpx-discipline-uploader/gpx-discipline-uploader.component';
import {SimulationHistoryListComponent} from './simulation-history-list/simulation-history-list.component';

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RouterLink, SportIconComponent, RouteMapComponent, GoalSidebarItemComponent, GoalCountdownHeroComponent, GpxDisciplineUploaderComponent, SimulationHistoryListComponent],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent implements OnInit {
  private raceGoalService = inject(RaceGoalService);
  private raceService = inject(RaceService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  allGoals$ = this.raceGoalService.goals$.pipe(map((goals) => this.sortGoals(goals)));

  // Selected goal (sidebar selection)
  selectedGoalId: string | null = null;

  // Modal state
  isFormOpen = false;
  editingGoal: RaceGoal | null = null;
  formStep: 'search' | 'details' = 'search';
  form: Partial<RaceGoal> = this.emptyForm();

  // Race search
  raceSearchQuery = '';
  private searchSubject = new BehaviorSubject<string>('');
  searchResults$ = this.searchSubject.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    switchMap((q) => (q.length >= 2 ? this.raceService.searchRaces(q) : of([]))),
  );
  selectedRace: Race | null = null;

  readonly isSavingGoal$ = new BehaviorSubject(false);

  // GPX upload
  gpxUploading: Record<string, boolean> = {};

  routeCache: Record<string, RouteCoordinate[]> = {};

  // Simulation requests per goal
  simRequestsCache: Record<string, SimulationRequest[]> = {};

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  get priorities(): Array<{ value: 'A' | 'B' | 'C'; label: string }> {
    return [
      { value: 'A', label: this.translate.instant('GOALS.PRIORITY_A') },
      { value: 'B', label: this.translate.instant('GOALS.PRIORITY_B') },
      { value: 'C', label: this.translate.instant('GOALS.PRIORITY_C') },
    ];
  }

  ngOnInit(): void {
    this.raceGoalService.loadGoals();

    // Auto-select nearest upcoming goal on first load
    this.allGoals$.pipe(take(1)).subscribe((goals) => {
      const upcoming = goals.filter((g) => this.isUpcoming(g));
      const toSelect = upcoming.length > 0 ? upcoming[0] : goals[0] ?? null;
      if (toSelect) this.selectGoal(toSelect);
    });

    // Handle raceId query param from /races page "ADD TO MY GOALS"
    this.route.queryParams.subscribe((params) => {
      const raceId = params['raceId'];
      if (raceId) {
        this.raceService.getRace(raceId).subscribe({
          next: (race) => {
            this.openCreate();
            this.selectRace(race);
            this.router.navigate([], { replaceUrl: true });
          },
        });
      }
    });
  }

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  weeksUntil(dateStr: string): number {
    return sharedWeeksUntil(dateStr);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  isUpcoming(goal: RaceGoal): boolean {
    return !goal.raceDate || this.daysUntil(goal.raceDate) >= 0;
  }

  getProgressPercent(goal: RaceGoal): number {
    if (!goal.createdAt || !goal.raceDate) return 0;
    const created = new Date(goal.createdAt).getTime();
    const race = new Date(goal.raceDate + 'T00:00:00').getTime();
    const now = Date.now();
    if (now >= race) return 100;
    if (now <= created) return 0;
    return Math.round(((now - created) / (race - created)) * 100);
  }

  // ── Sidebar selection ─────────────────────────────────────────────

  selectGoal(goal: RaceGoal): void {
    this.selectedGoalId = goal.id;
    if (goal.race) {
      this.loadRoutesForGoal(goal.race);
    }
    if (goal.id && !this.simRequestsCache[goal.id]) {
      this.loadSimulationRequests(goal);
    }
    this.cdr.markForCheck();
  }

  getSelectedGoal(goals: RaceGoal[]): RaceGoal | null {
    return goals.find((g) => g.id === this.selectedGoalId) ?? null;
  }

  // ── Modal: Create / Edit ──────────────────────────────────────────

  openCreate(): void {
    this.editingGoal = null;
    this.form = this.emptyForm();
    this.formStep = 'search';
    this.selectedRace = null;
    this.raceSearchQuery = '';
    this.isFormOpen = true;
  }

  openEdit(goal: RaceGoal): void {
    this.editingGoal = goal;
    this.form = { ...goal };
    this.formStep = 'details';
    this.selectedRace = goal.race ?? null;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  onSearchChange(query: string): void {
    this.searchSubject.next(query);
  }

  selectRace(race: Race): void {
    this.selectedRace = race;
    this.form.raceId = race.id;
    this.form.title = race.title;
    this.form.sport = race.sport as RaceGoal['sport'];
    if (race.location) this.form.location = race.location;
    if (race.distance) this.form.distance = race.distance;
    this.formStep = 'details';
    this.cdr.markForCheck();
  }

  backToSearch(): void {
    this.formStep = 'search';
    this.cdr.markForCheck();
  }

  save(): void {
    if (!this.form.title || this.isSavingGoal$.value) return;
    this.isSavingGoal$.next(true);
    if (this.editingGoal) {
      this.raceGoalService.updateGoal(this.editingGoal.id, this.form).subscribe({
        next: () => {
          this.isSavingGoal$.next(false);
          this.isFormOpen = false;
          this.cdr.markForCheck();
        },
        error: () => this.isSavingGoal$.next(false),
      });
    } else {
      this.raceGoalService.createGoal(this.form).subscribe({
        next: (created) => {
          this.isSavingGoal$.next(false);
          this.isFormOpen = false;
          if (created.race) this.loadRoutesForGoal(created.race);
          this.cdr.markForCheck();
        },
        error: () => this.isSavingGoal$.next(false),
      });
    }
  }

  delete(goal: RaceGoal): void {
    if (confirm(this.translate.instant('GOALS.DELETE_CONFIRM', { title: goal.title }))) {
      this.raceGoalService.deleteGoal(goal.id);
    }
  }

  // ── Race / Route Loading ──────────────────────────────────────────

  loadRoutesForGoal(race: Race): void {
    for (const disc of ['swim', 'bike', 'run']) {
      const key = race.id + '_' + disc;
      if (this.hasGpxForDiscipline(race, disc) && !this.routeCache[key]) {
        this.raceService.getRouteCoordinates(race.id, disc).subscribe({
          next: (coords) => {
            this.routeCache[key] = coords;
            this.cdr.markForCheck();
          },
        });
      }
    }
  }

  loadSimulationRequests(goal: RaceGoal): void {
    this.raceService.loadSimulationRequests(goal.id);
    this.raceService.simulationRequests$.subscribe({
      next: (reqs) => {
        this.simRequestsCache[goal.id] = reqs.filter((r) => r.goalId === goal.id);
        this.cdr.markForCheck();
      },
    });
  }

  getRace(goals: RaceGoal[], raceId: string): Race | null {
    return goals.find((g) => g.raceId === raceId)?.race ?? null;
  }

  getRouteCoordsForDiscipline(race: Race, disc: string): RouteCoordinate[] {
    return this.routeCache[race.id + '_' + disc] ?? [];
  }

  getSimRequests(goalId: string): SimulationRequest[] {
    return this.simRequestsCache[goalId] ?? [];
  }

  // ── GPX Upload ────────────────────────────────────────────────────

  onGpxFileSelected(event: Event, raceId: string, discipline: string): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.uploadGpx(raceId, discipline, input.files[0]);
    }
  }

  uploadGpx(raceId: string, discipline: string, file: File): void {
    if (!file.name.toLowerCase().endsWith('.gpx')) return;
    const key = raceId + '_' + discipline;
    this.gpxUploading[key] = true;
    this.cdr.markForCheck();

    this.raceService.uploadGpx(raceId, discipline, file).subscribe({
      next: () => {
        this.gpxUploading[key] = false;
        // Reload goals so the embedded race reflects the new GPX flags
        this.raceGoalService.loadGoals();
        // Reload route coords for the newly uploaded discipline
        this.raceService.getRouteCoordinates(raceId, discipline).subscribe({
          next: (coords) => {
            this.routeCache[key] = coords;
            this.cdr.markForCheck();
          },
        });
        this.cdr.markForCheck();
      },
      error: () => {
        this.gpxUploading[key] = false;
        this.cdr.markForCheck();
      },
    });
  }

  isGpxUploading(raceId: string, discipline: string): boolean {
    return this.gpxUploading[raceId + '_' + discipline] ?? false;
  }

  // ── Simulation ────────────────────────────────────────────────────

  simulateRace(goal: RaceGoal): void {
    const params: Record<string, string> = {};
    if (goal.raceId) params['raceId'] = goal.raceId;
    if (goal.id) params['goalId'] = goal.id;
    this.router.navigate(['/pacing'], { queryParams: params });
  }

  rerunSimulation(req: SimulationRequest): void {
    const params: Record<string, string> = {};
    if (req.raceId) params['raceId'] = req.raceId;
    if (req.goalId) params['goalId'] = req.goalId;
    this.router.navigate(['/pacing'], { queryParams: params });
  }

  deleteSimRequest(req: SimulationRequest): void {
    if (!req.id) return;
    this.raceService.deleteSimulationRequest(req.id).subscribe({
      next: () => {
        if (req.goalId) {
          this.simRequestsCache[req.goalId] = (this.simRequestsCache[req.goalId] ?? []).filter(
            (r) => r.id !== req.id,
          );
          this.cdr.markForCheck();
        }
      },
    });
  }

  formatSimLabel(req: SimulationRequest): string {
    return req.label ?? `${req.discipline} simulation`;
  }

  formatSimDate(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  // ── Discipline helpers ────────────────────────────────────────────

  formatDistance(meters?: number): string {
    if (!meters) return '';
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }

  getGpxDisciplines(race: Race): string[] {
    if (race.sport === 'TRIATHLON') return ['swim', 'bike', 'run'];
    if (race.sport === 'CYCLING') return ['bike'];
    if (race.sport === 'RUNNING') return ['run'];
    if (race.sport === 'SWIMMING') return ['swim'];
    return ['bike'];
  }

  hasGpxForDiscipline(race: Race, discipline: string): boolean {
    switch (discipline) {
      case 'swim': return !!race.hasSwimGpx;
      case 'bike': return !!race.hasBikeGpx;
      case 'run': return !!race.hasRunGpx;
      default: return false;
    }
  }

  // ── Sorting ───────────────────────────────────────────────────────

  private sortGoals(goals: RaceGoal[]): RaceGoal[] {
    const priorityOrder: Record<string, number> = { A: 0, B: 1, C: 2 };
    const upcoming = goals.filter((g) => this.isUpcoming(g));
    const past = goals.filter((g) => !this.isUpcoming(g));

    const sortFn = (a: RaceGoal, b: RaceGoal) => {
      const pa = priorityOrder[a.priority] ?? 3;
      const pb = priorityOrder[b.priority] ?? 3;
      if (pa !== pb) return pa - pb;
      return new Date(a.raceDate).getTime() - new Date(b.raceDate).getTime();
    };

    return [
      ...upcoming.sort(sortFn),
      ...past.sort((a, b) => new Date(b.raceDate).getTime() - new Date(a.raceDate).getTime()),
    ];
  }

  private emptyForm(): Partial<RaceGoal> {
    return { sport: 'CYCLING', priority: 'A' };
  }
}
