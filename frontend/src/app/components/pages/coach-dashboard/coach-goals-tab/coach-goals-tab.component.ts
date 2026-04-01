import {ChangeDetectionStrategy, Component, inject, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs';
import {RaceGoal, RaceGoalService} from '../../../../services/race-goal.service';
import {daysUntil as sharedDaysUntil} from '../../../shared/format/format.utils';

@Component({
  selector: 'app-coach-goals-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './coach-goals-tab.component.html',
  styleUrl: './coach-goals-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachGoalsTabComponent {
  @Input() athleteGoals$!: Observable<RaceGoal[]>;

  private readonly raceGoalService = inject(RaceGoalService);

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  trackGoalById(goal: RaceGoal): string {
    return goal.id;
  }
}
