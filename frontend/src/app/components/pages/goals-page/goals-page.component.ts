import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {BehaviorSubject, debounceTime, distinctUntilChanged, of, switchMap} from 'rxjs';
import {map} from 'rxjs/operators';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {Race, RaceService} from '../../../services/race.service';
import {SkeletonComponent} from '../../shared/skeleton/skeleton.component';
import {
  GoalTimelineComponent,
  TimelineItem,
  TimelinePriority,
} from '../../shared/goal-timeline/goal-timeline.component';

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    RouterLink,
    SkeletonComponent,
    GoalTimelineComponent,
  ],
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

  primaryGoal$ = this.allGoals$.pipe(map((goals) => this.findPrimaryGoal(goals)));
  pastGoals$ = this.allGoals$.pipe(
    map((goals) => goals.filter((g) => !this.isUpcoming(g)).slice(0, 5)),
  );

  timelineItems$ = this.allGoals$.pipe(
    map((goals) => {
      const primary = this.findPrimaryGoal(goals);
      return goals.map<TimelineItem<RaceGoal>>((g) => ({
        id: g.id,
        title: g.title,
        sport: g.sport,
        raceDate: g.race?.scheduledDate,
        priority: g.priority as TimelinePriority,
        isPrimary: !!primary && primary.id === g.id,
        data: g,
      }));
    }),
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

  // ── Date helpers ──────────────────────────────────────────────────

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

  onMarkerClick(item: TimelineItem<RaceGoal>): void {
    if (item.data) this.openEdit(item.data);
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

  formatDateShort(dateStr: string | undefined | null): string {
    const d = this.parseGoalDate(dateStr);
    if (!d) return 'Date à définir';
    const day = String(d.getDate()).padStart(2, '0');
    const month = d.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '').toUpperCase().slice(0, 3);
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
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
