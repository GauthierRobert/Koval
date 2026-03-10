import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClubService } from '../../../../../../services/club.service';

@Component({
  selector: 'app-club-stats-tab',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './club-stats-tab.component.html',
  styleUrl: './club-stats-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubStatsTabComponent {
  private clubService = inject(ClubService);
  weeklyStats$ = this.clubService.weeklyStats$;
}
