import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, map } from 'rxjs';
import { ChatRoomService } from '../../../services/chat-room.service';
import { ChatRoomScope, ChatRoomSummary } from '../../../models/chat.models';
import { ChatMessagePanelComponent } from './chat-message-panel/chat-message-panel.component';

interface RoomGroup {
  label: string;
  rooms: ChatRoomSummary[];
}

/**
 * Top-level chat page: sidebar of the user's rooms grouped by scope, plus the active
 * room's message panel. Mounted at routes {@code /messages} and {@code /messages/:roomId}.
 */
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly groupedRooms$ = this.chatRoomService.rooms$.pipe(map((rooms) => this.groupRooms(rooms)));
  readonly activeRoomId$ = this.chatRoomService.activeRoomId$;

  searchText = '';
  private subs = new Subscription();

  ngOnInit(): void {
    // Initializing is idempotent — it also ensures the SSE stream is running.
    this.chatRoomService.initialize();

    this.subs.add(
      this.route.paramMap.subscribe((params) => {
        const roomId = params.get('roomId');
        if (roomId) this.chatRoomService.openRoom(roomId);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  selectRoom(room: ChatRoomSummary): void {
    this.router.navigate(['/messages', room.id]);
  }

  trackById(_index: number, room: ChatRoomSummary): string {
    return room.id;
  }

  trackGroup(_index: number, group: RoomGroup): string {
    return group.label;
  }

  private groupRooms(rooms: ChatRoomSummary[]): RoomGroup[] {
    const filter = this.searchText.trim().toLowerCase();
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
