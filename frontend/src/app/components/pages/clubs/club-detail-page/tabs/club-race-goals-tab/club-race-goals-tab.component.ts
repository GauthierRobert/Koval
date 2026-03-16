import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClubService } from '../../../../../../services/club.service';

@Component({
  selector: 'app-club-race-goals-tab',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './club-race-goals-tab.component.html',
  styleUrl: './club-race-goals-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubRaceGoalsTabComponent {
  private clubService = inject(ClubService);
  raceGoals$ = this.clubService.raceGoals$;

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
