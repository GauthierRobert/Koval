import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { map } from 'rxjs/operators';
import { AuthService } from '../../../../../../services/auth.service';
import { ClubFeedService } from '../../../../../../services/club-feed.service';
import { RaceGoal, RaceGoalService } from '../../../../../../services/race-goal.service';
import { ClubRaceGoalResponse } from '../../../../../../models/club.model';
import {
  GoalTimelineComponent,
  TimelineItem,
  TimelinePriority,
} from '../../../../../shared/goal-timeline/goal-timeline.component';

type ViewMode = 'timeline' | 'list';
type AddPriority = 'A' | 'B' | 'C';

/** A group of past races within one Nov→Nov season, most recent race first. */
export interface SeasonGroup {
  /** Calendar year the season started in (its November). */
  startYear: number;
  label: string;
  races: ClubRaceGoalResponse[];
}

@Component({
  selector: 'app-club-race-goals-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RouterLink, GoalTimelineComponent],
  templateUrl: './club-race-goals-tab.component.html',
  styleUrl: './club-race-goals-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubRaceGoalsTabComponent {
  private clubFeedService = inject(ClubFeedService);
  private raceGoalService = inject(RaceGoalService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  raceGoals$ = this.clubFeedService.raceGoals$;
  currentUserId$ = this.authService.user$.pipe(map((u) => u?.id ?? null));

  /** Forward-looking goals (today or later, or undated) — drive the timeline/list views. */
  upcomingGoals$ = this.raceGoals$.pipe(map((goals) => goals.filter((g) => !g.past)));

  /** Past goals grouped by Nov→Nov season, most recent season first. */
  pastSeasons$ = this.raceGoals$.pipe(map((goals) => this.groupBySeason(goals)));

  /** Timeline as default per design. */
  view: ViewMode = 'timeline';

  /** Season startYears the user has collapsed; all expanded by default. */
  private collapsedSeasons = new Set<number>();

  timelineItems$ = this.upcomingGoals$.pipe(
    map((goals) =>
      goals.map<TimelineItem<ClubRaceGoalResponse>>((g) => ({
        id: this.rowKey(g),
        title: g.title,
        sport: g.sport,
        raceDate: g.raceDate,
        priority: this.derivePriority(g),
        distanceCategory: g.distanceCategory,
        data: g,
      })),
    ),
  );

  membersModalGoal: ClubRaceGoalResponse | null = null;
  addingKey: string | null = null;

  // Add-as-my-goal modal state
  addModalGoal: ClubRaceGoalResponse | null = null;
  addForm: { priority: AddPriority; targetTime: string; notes: string } = this.emptyAddForm();
  isSavingAdd = false;

  readonly addPriorities: AddPriority[] = ['A', 'B', 'C'];

  setView(mode: ViewMode): void {
    this.view = mode;
  }

  onTimelineItemClick(item: TimelineItem<ClubRaceGoalResponse>): void {
    if (item.data) this.membersModalGoal = item.data;
  }

  /** Highest priority among participants drives the marker color. */
  private derivePriority(goal: ClubRaceGoalResponse): TimelinePriority {
    if (goal.participants.some((p) => p.priority === 'A')) return 'A';
    if (goal.participants.some((p) => p.priority === 'B')) return 'B';
    return 'C';
  }

  openMembersModal(goal: ClubRaceGoalResponse, event: Event): void {
    event.stopPropagation();
    this.membersModalGoal = goal;
  }

  closeMembersModal(): void {
    this.membersModalGoal = null;
  }

  isParticipant(goal: ClubRaceGoalResponse, userId: string | null): boolean {
    return !!userId && goal.participants.some((p) => p.userId === userId);
  }

  openAddModal(goal: ClubRaceGoalResponse, event?: Event): void {
    event?.stopPropagation();
    this.addModalGoal = goal;
    this.addForm = this.emptyAddForm();
    this.membersModalGoal = null;
  }

  closeAddModal(): void {
    if (this.isSavingAdd) return;
    this.addModalGoal = null;
  }

  confirmAddToMyGoals(): void {
    const goal = this.addModalGoal;
    if (!goal || this.isSavingAdd) return;

    this.isSavingAdd = true;
    this.addingKey = this.rowKey(goal);
    this.cdr.markForCheck();

    const payload: Partial<RaceGoal> = {
      raceId: goal.raceId,
      title: goal.title,
      sport: goal.sport as RaceGoal['sport'],
      distance: goal.distance,
      location: goal.location,
      priority: this.addForm.priority,
      targetTime: this.addForm.targetTime?.trim() || undefined,
      notes: this.addForm.notes?.trim() || undefined,
    };

    this.raceGoalService.createGoal(payload).subscribe({
      next: () => {
        const clubId = this.route.parent?.snapshot.params['id'] ?? this.route.snapshot.params['id'];
        if (clubId) this.clubFeedService.loadRaceGoals(clubId);
        this.isSavingAdd = false;
        this.addingKey = null;
        this.addModalGoal = null;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isSavingAdd = false;
        this.addingKey = null;
        this.cdr.markForCheck();
      },
    });
  }

  isAdding(goal: ClubRaceGoalResponse): boolean {
    return this.addingKey === this.rowKey(goal);
  }

  private emptyAddForm(): { priority: AddPriority; targetTime: string; notes: string } {
    return { priority: 'A', targetTime: '', notes: '' };
  }

  private rowKey(goal: ClubRaceGoalResponse): string {
    return goal.raceId ?? `${goal.title}|${goal.raceDate ?? ''}`;
  }

  formatDate(dateStr: string | undefined | null): string {
    if (!dateStr) return '—';
    const d = new Date(dateStr + 'T00:00:00');
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  daysUntil(dateStr: string | undefined | null): number | null {
    if (!dateStr) return null;
    const race = new Date(dateStr + 'T00:00:00').getTime();
    if (isNaN(race)) return null;
    return Math.round((race - Date.now()) / 86400000);
  }

  getPriorityColor(priority: string): string {
    const map: Record<string, string> = { A: '#F59E0B', B: '#60A5FA', C: '#9CA3AF' };
    return map[priority] ?? '#9CA3AF';
  }

  /** The viewer's own linked session for a race, when one was recorded. */
  mySessionId(goal: ClubRaceGoalResponse, userId: string | null): string | null {
    if (!userId) return null;
    return goal.participants.find((p) => p.userId === userId)?.completedSessionId ?? null;
  }

  toggleSeason(startYear: number): void {
    if (this.collapsedSeasons.has(startYear)) this.collapsedSeasons.delete(startYear);
    else this.collapsedSeasons.add(startYear);
    this.cdr.markForCheck();
  }

  isSeasonCollapsed(startYear: number): boolean {
    return this.collapsedSeasons.has(startYear);
  }

  /**
   * Buckets past, dated races into seasons that run November→November. A race in Nov/Dec belongs
   * to the season that opened that November; Jan–Oct belongs to the previous November's season.
   */
  private groupBySeason(goals: ClubRaceGoalResponse[]): SeasonGroup[] {
    const bySeason = new Map<number, ClubRaceGoalResponse[]>();
    for (const g of goals) {
      if (!g.past || !g.raceDate) continue;
      const startYear = this.seasonStartYear(g.raceDate);
      const bucket = bySeason.get(startYear);
      if (bucket) bucket.push(g);
      else bySeason.set(startYear, [g]);
    }
    return [...bySeason.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([startYear, races]) => ({
        startYear,
        label: `${startYear} – ${startYear + 1}`,
        races: races.sort((a, b) => (b.raceDate ?? '').localeCompare(a.raceDate ?? '')),
      }));
  }

  private seasonStartYear(dateStr: string): number {
    const d = new Date(dateStr + 'T00:00:00');
    // getMonth() is 0-based; November = 10.
    return d.getMonth() >= 10 ? d.getFullYear() : d.getFullYear() - 1;
  }
}
