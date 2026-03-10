import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClubService } from '../../../../../../services/club.service';

@Component({
  selector: 'app-club-feed-tab',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './club-feed-tab.component.html',
  styleUrl: './club-feed-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubFeedTabComponent {
  private clubService = inject(ClubService);
  feed$ = this.clubService.feed$;

  getActivityIcon(type: string): string {
    switch (type) {
      case 'MEMBER_JOINED': return '👤';
      case 'MEMBER_LEFT': return '🚪';
      case 'SESSION_CREATED': return '📅';
      case 'SESSION_JOINED': return '✅';
      case 'TRAINING_CREATED': return '💪';
      case 'RACE_GOAL_ADDED': return '🏁';
      default: return '📌';
    }
  }

  getActivityText(type: string, actorName: string, targetTitle: string | undefined): string {
    switch (type) {
      case 'MEMBER_JOINED': return `${actorName} joined the club`;
      case 'MEMBER_LEFT': return `${actorName} left the club`;
      case 'SESSION_CREATED': return `${actorName} created session: ${targetTitle ?? ''}`;
      case 'SESSION_JOINED': return `${actorName} joined session: ${targetTitle ?? ''}`;
      case 'TRAINING_CREATED': return `${actorName} created a training`;
      case 'RACE_GOAL_ADDED': return `${actorName} added a race goal`;
      default: return `${actorName} did something`;
    }
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }
}
