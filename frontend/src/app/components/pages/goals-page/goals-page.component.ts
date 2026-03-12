import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { RaceGoal, RaceGoalService } from '../../../services/race-goal.service';
import { Race, RaceService, SimulationRequest, RouteCoordinate } from '../../../services/race.service';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import { RouteMapComponent } from '../pacing/route-map/route-map.component';
import { daysUntil as sharedDaysUntil, weeksUntil as sharedWeeksUntil } from '../../shared/format/format.utils';

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SportIconComponent, RouteMapComponent],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent implements OnInit {
  private raceGoalService = inject(RaceGoalService);
  private raceService = inject(RaceService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  goals$ = this.raceGoalService.goals$.pipe(map((goals) => this.sortGoals(goals)));

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
  isCreatingRace = false;
  isAiCompleting = false;
  newRaceTitle = '';

  // GPX upload
  gpxUploading: Record<string, boolean> = {};

  // Expanded goal card (for showing details, GPX, simulation)
  expandedGoalId: string | null = null;

  // Race details cache per goal
  raceCache: Record<string, Race> = {};
  routeCache: Record<string, RouteCoordinate[]> = {};

  // Simulation requests per goal
  simRequestsCache: Record<string, SimulationRequest[]> = {};

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  readonly priorities: Array<{ value: 'A' | 'B' | 'C'; label: string }> = [
    { value: 'A', label: 'A \u2014 Goal Race' },
    { value: 'B', label: 'B \u2014 Target Race' },
    { value: 'C', label: 'C \u2014 Training Race' },
  ];

  readonly monthNames = [
    '', 'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  ngOnInit(): void {
    this.raceGoalService.loadGoals();
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

  // ── Modal: Create / Edit ──────────────────────────────────────────

  openCreate(): void {
    this.editingGoal = null;
    this.form = this.emptyForm();
    this.formStep = 'search';
    this.selectedRace = null;
    this.raceSearchQuery = '';
    this.newRaceTitle = '';
    this.isCreatingRace = false;
    this.isFormOpen = true;
  }

  openEdit(goal: RaceGoal): void {
    this.editingGoal = goal;
    this.form = { ...goal };
    this.formStep = 'details';
    this.selectedRace = goal.raceId ? this.raceCache[goal.raceId] ?? null : null;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
    this.isAiCompleting = false;
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

  startCreateRace(): void {
    this.isCreatingRace = true;
    this.cdr.markForCheck();
  }

  createAndCompleteRace(): void {
    if (!this.newRaceTitle.trim()) return;
    this.isAiCompleting = true;
    this.cdr.markForCheck();

    this.raceService.createRace(this.newRaceTitle.trim()).subscribe({
      next: (race) => {
        this.raceService.aiComplete(race.id).subscribe({
          next: (completed) => {
            this.selectRace(completed);
            this.isAiCompleting = false;
            this.isCreatingRace = false;
            this.cdr.markForCheck();
          },
          error: () => {
            // AI completion failed, still use the basic race
            this.selectRace(race);
            this.isAiCompleting = false;
            this.isCreatingRace = false;
            this.cdr.markForCheck();
          },
        });
      },
      error: () => {
        this.isAiCompleting = false;
        this.cdr.markForCheck();
      },
    });
  }

  skipRaceSelection(): void {
    this.selectedRace = null;
    this.form.raceId = undefined;
    this.formStep = 'details';
    this.cdr.markForCheck();
  }

  backToSearch(): void {
    this.formStep = 'search';
    this.cdr.markForCheck();
  }

  save(): void {
    if (!this.form.title) return;
    if (this.editingGoal) {
      this.raceGoalService.updateGoal(this.editingGoal.id, this.form).subscribe(() => {
        this.isFormOpen = false;
        this.cdr.markForCheck();
      });
    } else {
      this.raceGoalService.createGoal(this.form).subscribe((created) => {
        this.isFormOpen = false;
        if (created.raceId) this.loadRaceForGoal(created);
        this.cdr.markForCheck();
      });
    }
  }

  delete(goal: RaceGoal): void {
    if (confirm(`Delete "${goal.title}"?`)) {
      this.raceGoalService.deleteGoal(goal.id);
    }
  }

  // ── Goal Card Expansion ───────────────────────────────────────────

  toggleExpand(goal: RaceGoal): void {
    if (this.expandedGoalId === goal.id) {
      this.expandedGoalId = null;
    } else {
      this.expandedGoalId = goal.id;
      if (goal.raceId && !this.raceCache[goal.raceId]) {
        this.loadRaceForGoal(goal);
      }
      if (goal.id && !this.simRequestsCache[goal.id]) {
        this.loadSimulationRequests(goal);
      }
    }
    this.cdr.markForCheck();
  }

  loadRaceForGoal(goal: RaceGoal): void {
    if (!goal.raceId) return;
    this.raceService.getRace(goal.raceId).subscribe({
      next: (race) => {
        this.raceCache[goal.raceId!] = race;
        // Load route coordinates for map preview
        if (race.hasBikeGpx) {
          this.raceService.getRouteCoordinates(race.id, 'bike').subscribe({
            next: (coords) => {
              this.routeCache[race.id + '_bike'] = coords;
              this.cdr.markForCheck();
            },
          });
        } else if (race.hasRunGpx) {
          this.raceService.getRouteCoordinates(race.id, 'run').subscribe({
            next: (coords) => {
              this.routeCache[race.id + '_run'] = coords;
              this.cdr.markForCheck();
            },
          });
        }
        this.cdr.markForCheck();
      },
    });
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

  getRace(raceId: string): Race | null {
    return this.raceCache[raceId] ?? null;
  }

  getRouteCoords(race: Race): RouteCoordinate[] {
    return this.routeCache[race.id + '_bike'] ?? this.routeCache[race.id + '_run'] ?? [];
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
        // Refresh race data
        this.raceService.getRace(raceId).subscribe({
          next: (race) => {
            this.raceCache[raceId] = race;
            this.cdr.markForCheck();
          },
        });
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

  // ── Discipline badges ─────────────────────────────────────────────

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
