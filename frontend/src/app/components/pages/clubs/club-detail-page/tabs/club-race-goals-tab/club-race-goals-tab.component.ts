import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {map} from 'rxjs/operators';
import {AuthService} from '../../../../../../services/auth.service';
import {ClubFeedService} from '../../../../../../services/club-feed.service';
import {RaceGoalService} from '../../../../../../services/race-goal.service';
import {ClubRaceGoalResponse} from '../../../../../../models/club.model';

@Component({
  selector: 'app-club-race-goals-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule],
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

  membersModalGoal: ClubRaceGoalResponse | null = null;
  addingKey: string | null = null;

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

  addToMyGoals(goal: ClubRaceGoalResponse): void {
    const key = this.rowKey(goal);
    if (this.addingKey) return;
    this.addingKey = key;
    this.cdr.markForCheck();

    this.raceGoalService
      .createGoal({
        raceId: goal.raceId,
        title: goal.title,
        sport: goal.sport as never,
        raceDate: goal.raceDate,
        distance: goal.distance,
        location: goal.location,
        priority: 'A',
      })
      .subscribe({
        next: () => {
          const clubId = this.route.parent?.snapshot.params['id'] ?? this.route.snapshot.params['id'];
          if (clubId) this.clubFeedService.loadRaceGoals(clubId);
          this.addingKey = null;
          this.cdr.markForCheck();
        },
        error: () => {
          this.addingKey = null;
          this.cdr.markForCheck();
        },
      });
  }

  isAdding(goal: ClubRaceGoalResponse): boolean {
    return this.addingKey === this.rowKey(goal);
  }

  private rowKey(goal: ClubRaceGoalResponse): string {
    return goal.raceId ?? `${goal.title}|${goal.raceDate}`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
    });
  }

  daysUntil(dateStr: string): number {
    const race = new Date(dateStr + 'T00:00:00').getTime();
    return Math.round((race - Date.now()) / 86400000);
  }

  getPriorityColor(priority: string): string {
    const map: Record<string, string> = { A: '#F59E0B', B: '#60A5FA', C: '#9CA3AF' };
    return map[priority] ?? '#9CA3AF';
  }
}
