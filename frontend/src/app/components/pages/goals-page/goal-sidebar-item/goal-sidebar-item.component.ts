import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {RaceGoal} from '../../../../services/race-goal.service';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {daysUntil as sharedDaysUntil} from '../../../shared/format/format.utils';

@Component({
  selector: 'app-goal-sidebar-item',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './goal-sidebar-item.component.html',
  styleUrl: './goal-sidebar-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalSidebarItemComponent {
  @Input() goal!: RaceGoal;
  @Input() isSelected = false;
  @Input() isUpcoming = true;

  @Output() select = new EventEmitter<RaceGoal>();

  getPriorityColor(priority: string): string {
    const colors: Record<string, string> = { A: '#F59E0B', B: '#60A5FA', C: '#9CA3AF' };
    return colors[priority] ?? '#9CA3AF';
  }

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
