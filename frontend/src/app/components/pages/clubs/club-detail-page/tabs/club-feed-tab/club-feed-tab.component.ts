import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ClubService} from '../../../../../../services/club.service';

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

  getActivityColor(type: string): string {
    switch (type) {
      case 'MEMBER_JOINED':    return '#22c55e';
      case 'SESSION_JOINED':   return '#22c55e';
      case 'MEMBER_LEFT':      return '#6b7280';
      case 'SESSION_CREATED':  return '#3b82f6';
      case 'SESSION_CANCELLED': return '#ef4444';
      case 'TRAINING_CREATED': return '#ff9d00';
      case 'RACE_GOAL_ADDED':  return '#f59e0b';
      default:                 return '#8e8ea0';
    }
  }

  getActivityText(type: string, actorName: string, targetTitle: string | undefined): string {
    switch (type) {
      case 'MEMBER_JOINED': return `${actorName} joined the club`;
      case 'MEMBER_LEFT': return `${actorName} left the club`;
      case 'SESSION_CREATED': return `${actorName} created session: ${targetTitle ?? ''}`;
      case 'SESSION_JOINED': return `${actorName} joined session: ${targetTitle ?? ''}`;
      case 'SESSION_CANCELLED': return `${actorName} cancelled session: ${targetTitle ?? ''}`;
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
