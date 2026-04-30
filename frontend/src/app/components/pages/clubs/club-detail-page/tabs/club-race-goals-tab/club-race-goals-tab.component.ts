import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {map} from 'rxjs/operators';
import {AuthService} from '../../../../../../services/auth.service';
import {ClubFeedService} from '../../../../../../services/club-feed.service';
import {RaceGoal, RaceGoalService} from '../../../../../../services/race-goal.service';
import {ClubRaceGoalResponse} from '../../../../../../models/club.model';
import {
  GoalTimelineComponent,
  TimelineItem,
  TimelinePriority,
} from '../../../../../shared/goal-timeline/goal-timeline.component';

type ViewMode = 'timeline' | 'list';
type AddPriority = 'A' | 'B' | 'C';

@Component({
  selector: 'app-club-race-goals-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, GoalTimelineComponent],
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

  /** Timeline as default per design. */
  view: ViewMode = 'timeline';

  timelineItems$ = this.raceGoals$.pipe(
    map((goals) =>
      goals.map<TimelineItem<ClubRaceGoalResponse>>((g) => ({
        id: this.rowKey(g),
        title: g.title,
        sport: g.sport,
        raceDate: g.raceDate,
        priority: this.derivePriority(g),
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
        const clubId =
          this.route.parent?.snapshot.params['id'] ?? this.route.snapshot.params['id'];
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
      weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
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
}
