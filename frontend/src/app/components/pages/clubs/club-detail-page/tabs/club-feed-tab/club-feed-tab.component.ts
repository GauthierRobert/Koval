import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ClubService} from '../../../../../../services/club.service';

@Component({
  selector: 'app-club-feed-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './club-feed-tab.component.html',
  styleUrl: './club-feed-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubFeedTabComponent {
  private clubService = inject(ClubService);
  private translate = inject(TranslateService);
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
    const params = { actorName, targetTitle: targetTitle ?? '' };
    switch (type) {
      case 'MEMBER_JOINED': return this.translate.instant('CLUB_FEED.ACTIVITY_MEMBER_JOINED', params);
      case 'MEMBER_LEFT': return this.translate.instant('CLUB_FEED.ACTIVITY_MEMBER_LEFT', params);
      case 'SESSION_CREATED': return this.translate.instant('CLUB_FEED.ACTIVITY_SESSION_CREATED', params);
      case 'SESSION_JOINED': return this.translate.instant('CLUB_FEED.ACTIVITY_SESSION_JOINED', params);
      case 'SESSION_CANCELLED': return this.translate.instant('CLUB_FEED.ACTIVITY_SESSION_CANCELLED', params);
      case 'TRAINING_CREATED': return this.translate.instant('CLUB_FEED.ACTIVITY_TRAINING_CREATED', params);
      case 'RACE_GOAL_ADDED': return this.translate.instant('CLUB_FEED.ACTIVITY_RACE_GOAL_ADDED', params);
      default: return this.translate.instant('CLUB_FEED.ACTIVITY_DEFAULT', params);
    }
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }
}
