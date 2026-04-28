import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {BehaviorSubject, combineLatest, debounceTime, distinctUntilChanged, of, switchMap} from 'rxjs';
import {map} from 'rxjs/operators';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Race, RaceService} from '../../../services/race.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {SkeletonComponent} from '../../shared/skeleton/skeleton.component';

type LaneKey = 'run' | 'tri' | 'bike';

interface RoadmapMarker extends RaceGoal {
  _x: number;
  _lane: LaneKey;
  _statusKey: 'A' | 'B' | 'C' | 'PASSED';
  _statusLabel: string;
  _short: string;
  _dateShort: string;
  _passed: boolean;
  _isPrimary: boolean;
}

interface RoadmapMonth {
  label: string;
}

interface RoadmapData {
  months: RoadmapMonth[];
  todayX: number;
  todayShort: string;
  windowStartLabel: string;
  windowEndLabel: string;
  markersByLane: Record<LaneKey, RoadmapMarker[]>;
}

interface LaneDef {
  key: LaneKey;
  label: string;
  icon: 'RUNNING' | 'CYCLING' | 'SWIMMING' | 'BRICK';
}

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RouterLink, SportIconComponent, SkeletonComponent],
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
  loading$ = this.raceGoalService.loading$;

  readonly lanes: LaneDef[] = [
    { key: 'run', label: 'RUN', icon: 'RUNNING' },
    { key: 'tri', label: 'TRI', icon: 'BRICK' },
    { key: 'bike', label: 'BIKE', icon: 'CYCLING' },
  ];

  private windowMonthsSubject = new BehaviorSubject<{ start: Date; end: Date }>(this.computeWindow());

  roadmap$ = combineLatest([this.allGoals$, this.windowMonthsSubject]).pipe(
    map(([goals, window]) => this.buildRoadmap(goals, window.start, window.end)),
  );

  primaryGoal$ = this.allGoals$.pipe(map((goals) => this.findPrimaryGoal(goals)));
  pastGoals$ = this.allGoals$.pipe(
    map((goals) => goals.filter((g) => !this.isUpcoming(g)).slice(0, 5)),
  );

  // Modal state
  isFormOpen = false;
  editingGoal: RaceGoal | null = null;
  formStep: 'search' | 'details' = 'search';
  form: Partial<RaceGoal> = this.emptyForm();

  raceSearchQuery = '';
  private searchSubject = new BehaviorSubject<string>('');
  searchResults$ = this.searchSubject.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    switchMap((q) => (q.length >= 2 ? this.raceService.searchRaces(q) : of([]))),
  );
  selectedRace: Race | null = null;

  readonly isSavingGoal$ = new BehaviorSubject(false);

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

  // ── Roadmap window ────────────────────────────────────────────────

  private computeWindow(): { start: Date; end: Date } {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    const end = new Date(start.getFullYear() + 1, start.getMonth(), 1);
    return { start, end };
  }

  private buildRoadmap(goals: RaceGoal[], start: Date, end: Date): RoadmapData {
    const span = end.getTime() - start.getTime();
    const now = new Date();
    const todayX = Math.max(0, Math.min(100, ((now.getTime() - start.getTime()) / span) * 100));

    const months: RoadmapMonth[] = [];
    for (let i = 0; i <= 12; i++) {
      const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
      months.push({ label: this.monthShort(d) });
    }

    const primary = this.findPrimaryGoal(goals);
    const markersByLane: Record<LaneKey, RoadmapMarker[]> = { run: [], tri: [], bike: [] };

    for (const g of goals) {
      const effectiveDateStr = g.race?.scheduledDate;
      const d = this.parseGoalDate(effectiveDateStr);
      if (!d) continue;
      if (d < start || d > end) continue;
      const x = ((d.getTime() - start.getTime()) / span) * 100;
      const lane = this.laneOfSport(g.sport);
      const passed = !this.isUpcoming(g);
      const marker: RoadmapMarker = {
        ...g,
        _x: x,
        _lane: lane,
        _passed: passed,
        _statusKey: passed ? 'PASSED' : (g.priority ?? 'C'),
        _statusLabel: passed ? '✓' : (g.priority ?? 'C'),
        _short: this.shortLabelFor(g),
        _dateShort: this.formatDateShort(effectiveDateStr),
        _isPrimary: !!primary && primary.id === g.id,
      };
      markersByLane[lane].push(marker);
    }

    return {
      months,
      todayX,
      todayShort: this.formatToday(now),
      windowStartLabel: this.monthLong(start),
      windowEndLabel: this.monthLong(new Date(end.getFullYear(), end.getMonth(), 1)),
      markersByLane,
    };
  }

  private parseGoalDate(dateStr: string | undefined | null): Date | null {
    if (!dateStr) return null;
    const direct = new Date(dateStr);
    if (!isNaN(direct.getTime())) return direct;
    const padded = new Date(dateStr + 'T00:00:00');
    if (!isNaN(padded.getTime())) return padded;
    return null;
  }

  // ── Stats helpers (used in template via primaryGoal$ + allGoals$) ──

  countActive(goals: RaceGoal[]): number {
    return goals.filter((g) => this.isUpcoming(g)).length;
  }

  findPrimaryGoal(goals: RaceGoal[]): RaceGoal | null {
    const sortByDate = (a: RaceGoal, b: RaceGoal) =>
      (this.effectiveDate(a) ?? '9999').localeCompare(this.effectiveDate(b) ?? '9999');
    const upcomingA = goals.filter((g) => this.isUpcoming(g) && g.priority === 'A').sort(sortByDate);
    if (upcomingA.length > 0) return upcomingA[0];
    const upcoming = goals.filter((g) => this.isUpcoming(g)).sort(sortByDate);
    return upcoming[0] ?? null;
  }

  findNextB(goals: RaceGoal[]): RaceGoal | null {
    return goals
      .filter((g) => this.isUpcoming(g) && g.priority === 'B')
      .sort((a, b) => (this.effectiveDate(a) ?? '9999').localeCompare(this.effectiveDate(b) ?? '9999'))[0] ?? null;
  }

  daysUntil(dateStr: string | undefined | null): number | null {
    const d = this.parseGoalDate(dateStr);
    if (!d) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(d);
    target.setHours(0, 0, 0, 0);
    return Math.round((target.getTime() - today.getTime()) / 86400000);
  }

  effectiveDate(goal: RaceGoal): string | undefined {
    return goal.race?.scheduledDate;
  }

  isUpcoming(goal: RaceGoal): boolean {
    const days = this.daysUntil(this.effectiveDate(goal));
    return days === null || days >= 0;
  }

  // ── Click handlers ────────────────────────────────────────────────

  onMarkerClick(goal: RaceGoal): void {
    this.openEdit(goal);
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
        next: () => {
          this.isSavingGoal$.next(false);
          this.isFormOpen = false;
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

  // ── Formatting helpers ────────────────────────────────────────────

  private laneOfSport(sport: RaceGoal['sport']): LaneKey {
    switch (sport) {
      case 'RUNNING': return 'run';
      case 'CYCLING': return 'bike';
      case 'TRIATHLON': return 'tri';
      case 'SWIMMING': return 'tri';
      default: return 'bike';
    }
  }

  private shortLabelFor(g: RaceGoal): string {
    const text = `${g.title ?? ''} ${g.distance ?? ''}`.toLowerCase();
    if (g.sport === 'RUNNING') {
      if (/marathon|42/.test(text)) return 'MAR';
      if (/semi|21|half/.test(text)) return '21K';
      if (/10\s?k|10km/.test(text)) return '10K';
      if (/5\s?k|5km/.test(text)) return '5K';
      return 'RUN';
    }
    if (g.sport === 'CYCLING') {
      if (/etape|granfondo|gravel|cyclo/.test(text)) return 'GRF';
      return 'BIKE';
    }
    if (g.sport === 'SWIMMING') return 'SWIM';
    if (g.sport === 'TRIATHLON') {
      if (/ironman|140\.6/.test(text)) return 'IM';
      if (/70\.3|half/.test(text)) return '70.3';
      if (/olympic|olympique/.test(text)) return 'OLY';
      if (/sprint/.test(text)) return 'SPR';
      return 'TRI';
    }
    return '—';
  }

  private monthShort(d: Date): string {
    return d.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '').toUpperCase().slice(0, 3);
  }

  private monthLong(d: Date): string {
    return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }

  formatDateShort(dateStr: string | undefined | null): string {
    const d = this.parseGoalDate(dateStr);
    if (!d) return 'Date à définir';
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  private formatToday(d: Date): string {
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    return `${day} ${month}`;
  }

  // ── Sorting ───────────────────────────────────────────────────────

  private sortGoals(goals: RaceGoal[]): RaceGoal[] {
    const priorityOrder: Record<string, number> = { A: 0, B: 1, C: 2 };
    const upcoming = goals.filter((g) => this.isUpcoming(g));
    const past = goals.filter((g) => !this.isUpcoming(g));
    const dateMs = (g: RaceGoal): number => {
      const d = this.parseGoalDate(this.effectiveDate(g));
      return d ? d.getTime() : Number.POSITIVE_INFINITY;
    };

    const sortFn = (a: RaceGoal, b: RaceGoal) => {
      const pa = priorityOrder[a.priority] ?? 3;
      const pb = priorityOrder[b.priority] ?? 3;
      if (pa !== pb) return pa - pb;
      return dateMs(a) - dateMs(b);
    };

    return [
      ...upcoming.sort(sortFn),
      ...past.sort((a, b) => dateMs(b) - dateMs(a)),
    ];
  }

  private emptyForm(): Partial<RaceGoal> {
    return { sport: 'CYCLING', priority: 'A' };
  }
}
