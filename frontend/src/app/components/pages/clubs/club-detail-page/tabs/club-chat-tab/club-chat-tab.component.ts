import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../../../../../services/auth.service';
import { ClubService } from '../../../../../../services/club.service';
import { ClubFeedService } from '../../../../../../services/club-feed.service';
import { EmbeddedChatComponent } from '../../../../../shared/embedded-chat/embedded-chat.component';
import { ChatRoomScope } from '../../../../../../models/chat.models';
import { ClubGroup, ClubRaceGoalResponse } from '../../../../../../models/club.model';

interface ChatTarget {
  key: string;
  scope: ChatRoomScope;
  refId?: string;
  title: string;
  badge: 'CLUB' | 'GROUP' | 'OBJECTIVE';
}

/**
 * Hub for every chat surface attached to a club:
 * - the CLUB-wide room (always present)
 * - one GROUP room per club group the current user belongs to
 * - one OBJECTIVE room per club race goal the current user is engaged in
 */
@Component({
  selector: 'app-club-chat-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, EmbeddedChatComponent],
  templateUrl: './club-chat-tab.component.html',
  styleUrl: './club-chat-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubChatTabComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) clubId!: string;

  private readonly authService = inject(AuthService);
  private readonly clubService = inject(ClubService);
  private readonly clubFeedService = inject(ClubFeedService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly currentUserId$ = this.authService.user$.pipe(map((u) => u?.id ?? null));

  readonly club$ = this.clubService.selectedClub$;

  readonly targets$ = combineLatest([
    this.authService.user$,
    this.clubService.groups$,
    this.clubFeedService.raceGoals$,
  ]).pipe(map(([user, groups, raceGoals]) => this.buildTargets(user?.id ?? null, groups, raceGoals)));

  selected: ChatTarget | null = null;
  view: 'list' | 'chat' = 'list';
  private subs = new Subscription();

  ngOnInit(): void {
    this.clubService.loadGroups(this.clubId);
    this.clubFeedService.loadRaceGoals(this.clubId);
    this.selected = this.defaultTarget();

    this.subs.add(
      this.targets$.subscribe((targets) => {
        const stillExists = this.selected && targets.some((t) => t.key === this.selected!.key);
        if (!stillExists) this.selected = targets[0] ?? this.defaultTarget();
        this.cdr.markForCheck();
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['clubId'] && !changes['clubId'].firstChange) {
      this.clubService.loadGroups(this.clubId);
      this.clubFeedService.loadRaceGoals(this.clubId);
      this.selected = this.defaultTarget();
      this.view = 'list';
    }
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  selectTarget(target: ChatTarget): void {
    this.selected = target;
    this.view = 'chat';
  }

  backToList(): void {
    this.view = 'list';
  }

  trackByKey(_index: number, target: ChatTarget): string {
    return target.key;
  }

  private defaultTarget(): ChatTarget {
    return { key: `CLUB:${this.clubId}`, scope: 'CLUB', title: 'Club', badge: 'CLUB' };
  }

  private buildTargets(
    userId: string | null,
    groups: ClubGroup[],
    raceGoals: ClubRaceGoalResponse[],
  ): ChatTarget[] {
    const targets: ChatTarget[] = [this.defaultTarget()];

    if (userId) {
      for (const group of groups) {
        if (group.clubId !== this.clubId) continue;
        if (!group.memberIds.includes(userId)) continue;
        targets.push({
          key: `GROUP:${group.id}`,
          scope: 'GROUP',
          refId: group.id,
          title: group.name,
          badge: 'GROUP',
        });
      }

      for (const goal of raceGoals) {
        if (!goal.participants.some((p) => p.userId === userId)) continue;
        const refId = this.objectiveKey(goal);
        targets.push({
          key: `OBJECTIVE:${refId}`,
          scope: 'OBJECTIVE',
          refId,
          title: goal.title,
          badge: 'OBJECTIVE',
        });
      }
    }

    return targets;
  }

  private objectiveKey(goal: ClubRaceGoalResponse): string {
    return goal.raceId ?? `${goal.title}|${goal.raceDate}`;
  }
}
