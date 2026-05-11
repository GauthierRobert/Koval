import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../../../../../services/auth.service';
import { ClubService } from '../../../../../../services/club.service';
import { ClubFeedService } from '../../../../../../services/club-feed.service';
import { ChatApiService } from '../../../../../../services/chat-api.service';
import { ChatSseService } from '../../../../../../services/chat-sse.service';
import { EmbeddedChatComponent } from '../../../../../shared/embedded-chat/embedded-chat.component';
import { ChatMessage, ChatRoomScope, ChatRoomSummary } from '../../../../../../models/chat.models';
import { ClubGroup, ClubRaceGoalResponse } from '../../../../../../models/club.model';

interface ChatTarget {
  key: string;
  scope: ChatRoomScope;
  refId?: string;
  title: string;
  badge: 'CLUB' | 'GROUP' | 'OBJECTIVE';
  /** Preview of the latest message in the matching room, if any. */
  lastMessagePreview?: string | null;
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
  @Output() viewChange = new EventEmitter<'list' | 'chat'>();

  private readonly authService = inject(AuthService);
  private readonly clubService = inject(ClubService);
  private readonly clubFeedService = inject(ClubFeedService);
  private readonly chatApi = inject(ChatApiService);
  private readonly chatSse = inject(ChatSseService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly currentUserId$ = this.authService.user$.pipe(map((u) => u?.id ?? null));

  readonly club$ = this.clubService.selectedClub$;

  // Local rooms list, populated only on tab mount. Used to render
  // last-message previews on the chat target list — no global state.
  private readonly roomsSubject = new BehaviorSubject<ChatRoomSummary[]>([]);
  readonly rooms$ = this.roomsSubject.asObservable();

  readonly targets$ = combineLatest([
    this.authService.user$,
    this.clubService.groups$,
    this.clubFeedService.raceGoals$,
    this.rooms$,
  ]).pipe(
    map(([user, groups, raceGoals, rooms]) =>
      this.buildTargets(user?.id ?? null, groups, raceGoals, rooms),
    ),
  );

  selected: ChatTarget | null = null;
  view: 'list' | 'chat' = 'list';
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.clubService.loadGroups(this.clubId);
    this.clubFeedService.loadRaceGoals(this.clubId);
    this.selected = this.defaultTarget();

    // Open the SSE stream for this chat-tab visit and fetch the rooms list
    // once. The stream is shut down in ngOnDestroy when the user leaves the tab.
    this.chatSse.connect();

    this.chatApi.getRooms().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (rooms) => {
        this.roomsSubject.next(rooms);
        this.cdr.markForCheck();
      },
      error: () => this.roomsSubject.next([]),
    });

    this.chatSse.onChatMessage$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((msg) => this.handleIncomingMessage(msg));

    this.targets$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((targets) => {
      const stillExists = this.selected && targets.some((t) => t.key === this.selected!.key);
      if (!stillExists) this.selected = targets[0] ?? this.defaultTarget();
      this.cdr.markForCheck();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['clubId'] && !changes['clubId'].firstChange) {
      this.clubService.loadGroups(this.clubId);
      this.clubFeedService.loadRaceGoals(this.clubId);
      this.selected = this.defaultTarget();
      this.setView('list');
    }
  }

  ngOnDestroy(): void {
    // takeUntilDestroyed drops subscriptions before this fires; just close the
    // underlying SSE stream and cancel its reconnect timer.
    this.chatSse.disconnect();
  }

  selectTarget(target: ChatTarget): void {
    this.selected = target;
    this.setView('chat');
  }

  backToList(): void {
    this.setView('list');
  }

  private setView(view: 'list' | 'chat'): void {
    if (this.view === view) return;
    this.view = view;
    this.viewChange.emit(view);
  }

  trackByKey(_index: number, target: ChatTarget): string {
    return target.key;
  }

  private defaultTarget(): ChatTarget {
    return { key: `CLUB:${this.clubId}`, scope: 'CLUB', title: 'Club', badge: 'CLUB' };
  }

  /** Keep last-message previews live while the chat tab is open. */
  private handleIncomingMessage(msg: ChatMessage): void {
    const rooms = this.roomsSubject.value;
    const idx = rooms.findIndex((r) => r.id === msg.roomId);
    if (idx < 0) return;
    const next = [...rooms];
    next[idx] = {
      ...next[idx],
      lastMessageAt: msg.createdAt,
      lastMessagePreview: msg.content,
      lastMessageSenderId: msg.senderId,
    };
    this.roomsSubject.next(next);
    this.cdr.markForCheck();
  }

  private buildTargets(
    userId: string | null,
    groups: ClubGroup[],
    raceGoals: ClubRaceGoalResponse[],
    rooms: ChatRoomSummary[],
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

    return targets.map((t) => ({
      ...t,
      lastMessagePreview: this.findRoomPreview(t, rooms),
    }));
  }

  private findRoomPreview(target: ChatTarget, rooms: ChatRoomSummary[]): string | null {
    const room = rooms.find(
      (r) =>
        r.scope === target.scope &&
        r.clubId === this.clubId &&
        (r.scopeRefId ?? null) === (target.refId ?? null),
    );
    return room?.lastMessagePreview ?? null;
  }

  private objectiveKey(goal: ClubRaceGoalResponse): string {
    return goal.raceId ?? `${goal.title}|${goal.raceDate ?? ''}`;
  }
}
