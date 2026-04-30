import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, map, combineLatest, BehaviorSubject, Observable } from 'rxjs';
import { ChatRoomService } from '../../../services/chat-room.service';
import { ChatNotificationService } from '../../../services/chat-notification.service';
import { ChatRoomScope, ChatRoomSummary } from '../../../models/chat.models';
import { ChatMessagePanelComponent } from './chat-message-panel/chat-message-panel.component';

interface RoomGroup {
  label: string;
  rooms: ChatRoomSummary[];
}

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatMessagePanelComponent],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPageComponent implements OnInit, OnDestroy {
  readonly chatRoomService = inject(ChatRoomService);
  private readonly chatNotifications = inject(ChatNotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly activeRoomId$ = this.chatRoomService.activeRoomId$;

  searchText = '';
  showNewDmDialog = false;
  newDmUserId = '';
  newDmLoading = false;

  /** Emit whenever the user types in the search box (purely client-side filter). */
  private readonly searchSubject = new BehaviorSubject('');
  readonly groupedRooms$: Observable<RoomGroup[]> = combineLatest([
    this.chatRoomService.rooms$,
    this.searchSubject,
  ]).pipe(
    map(([rooms, search]) => this.groupRooms(rooms, search)),
  );

  private subs = new Subscription();

  ngOnInit(): void {
    this.chatRoomService.initialize();
    this.subs.add(
      this.route.paramMap.subscribe((params) => {
        const roomId = params.get('roomId');
        if (roomId) {
          this.chatRoomService.openRoom(roomId);
          this.chatNotifications.notifyRoomOpened(roomId);
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  onSearchChange(text: string): void {
    this.searchSubject.next(text.trim().toLowerCase());
  }

  selectRoom(room: ChatRoomSummary): void {
    this.router.navigate(['/messages', room.id]);
  }

  openNewDmDialog(): void {
    this.showNewDmDialog = true;
    this.newDmUserId = '';
    this.cdr.markForCheck();
  }

  closeNewDmDialog(): void {
    this.showNewDmDialog = false;
    this.cdr.markForCheck();
  }

  startDm(): void {
    const id = this.newDmUserId.trim();
    if (!id || this.newDmLoading) return;
    this.newDmLoading = true;
    this.chatRoomService.openDirectWith(id).subscribe({
      next: (detail) => {
        this.showNewDmDialog = false;
        this.newDmLoading = false;
        this.router.navigate(['/messages', detail.id]);
        this.cdr.markForCheck();
      },
      error: () => {
        this.newDmLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  trackById(_index: number, room: ChatRoomSummary): string {
    return room.id;
  }

  trackGroup(_index: number, group: RoomGroup): string {
    return group.label;
  }

  private groupRooms(rooms: ChatRoomSummary[], filter: string): RoomGroup[] {
    const filtered = filter
      ? rooms.filter((r) => r.title.toLowerCase().includes(filter))
      : rooms;

    const buckets: Record<ChatRoomScope, ChatRoomSummary[]> = {
      DIRECT: [],
      CLUB: [],
      GROUP: [],
      OBJECTIVE: [],
      RECURRING_SESSION: [],
      SINGLE_SESSION: [],
    };

    for (const room of filtered) {
      buckets[room.scope].push(room);
    }

    const order: Array<{ scope: ChatRoomScope; label: string }> = [
      { scope: 'DIRECT', label: 'Direct messages' },
      { scope: 'CLUB', label: 'Clubs' },
      { scope: 'GROUP', label: 'Groups' },
      { scope: 'OBJECTIVE', label: 'Objectives' },
      { scope: 'RECURRING_SESSION', label: 'Recurring sessions' },
      { scope: 'SINGLE_SESSION', label: 'Sessions' },
    ];

    return order
      .map(({ scope, label }) => ({ label, rooms: buckets[scope] }))
      .filter((group) => group.rooms.length > 0);
  }
}
