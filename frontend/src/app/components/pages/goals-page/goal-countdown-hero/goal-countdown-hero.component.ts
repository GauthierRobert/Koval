import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {RaceGoal} from '../../../../services/race-goal.service';
import {daysUntil as sharedDaysUntil, weeksUntil as sharedWeeksUntil} from '../../../shared/format/format.utils';

@Component({
  selector: 'app-goal-countdown-hero',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './goal-countdown-hero.component.html',
  styleUrl: './goal-countdown-hero.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalCountdownHeroComponent {
  @Input() goal!: RaceGoal;

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  weeksUntil(dateStr: string): number {
    return sharedWeeksUntil(dateStr);
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

  getUrgencyColor(): string {
    if (!this.goal.raceDate) return 'var(--accent-color, #ff9d00)';
    const days = this.daysUntil(this.goal.raceDate);
    if (days <= 14) return '#ef4444';
    if (days <= 30) return '#f59e0b';
    return 'var(--accent-color, #ff9d00)';
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  isUpcoming(): boolean {
    return !this.goal.raceDate || this.daysUntil(this.goal.raceDate) >= 0;
  }
}
