import {inject, Injectable, NgZone} from '@angular/core';
import {Subject} from 'rxjs';
import {environment} from '../../environments/environment';
import {
  ClubFeedEventResponse,
  CommentDeletedPayload,
  CommentUpdatePayload,
  CompletionUpdatePayload,
  FeedEventDeletedPayload,
  ReactionUpdatePayload,
} from './club.service';

export interface KudosUpdatePayload {
  feedEventId: string;
  givenByUserId: string;
  successCount: number;
}

@Injectable({providedIn: 'root'})
export class ClubFeedSseService {
  private ngZone = inject(NgZone);
  private abortController: AbortController | null = null;
  private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
  private currentClubId: string | null = null;

  private completionUpdateSubject = new Subject<CompletionUpdatePayload>();
  onCompletionUpdate$ = this.completionUpdateSubject.asObservable();

  private newFeedEventSubject = new Subject<ClubFeedEventResponse>();
  onNewFeedEvent$ = this.newFeedEventSubject.asObservable();

  private kudosUpdateSubject = new Subject<KudosUpdatePayload>();
  onKudosUpdate$ = this.kudosUpdateSubject.asObservable();

  private commentUpdateSubject = new Subject<CommentUpdatePayload>();
  onCommentUpdate$ = this.commentUpdateSubject.asObservable();

  private commentEditedSubject = new Subject<CommentUpdatePayload>();
  onCommentEdited$ = this.commentEditedSubject.asObservable();

  private commentDeletedSubject = new Subject<CommentDeletedPayload>();
  onCommentDeleted$ = this.commentDeletedSubject.asObservable();

  private feedEventUpdatedSubject = new Subject<ClubFeedEventResponse>();
  onFeedEventUpdated$ = this.feedEventUpdatedSubject.asObservable();

  private feedEventDeletedSubject = new Subject<FeedEventDeletedPayload>();
  onFeedEventDeleted$ = this.feedEventDeletedSubject.asObservable();

  private reactionUpdateSubject = new Subject<ReactionUpdatePayload>();
  onReactionUpdate$ = this.reactionUpdateSubject.asObservable();

  private commentReplyAddedSubject = new Subject<CommentUpdatePayload>();
  onCommentReplyAdded$ = this.commentReplyAddedSubject.asObservable();

  connect(clubId: string): void {
    this.disconnect();
    this.currentClubId = clubId;

    const token = localStorage.getItem('token');
    if (!token) return;

    this.abortController = new AbortController();

    this.ngZone.runOutsideAngular(() => {
      this.startStream(clubId, token);
    });
  }

  disconnect(): void {
    this.currentClubId = null;
    this.abortController?.abort();
    this.abortController = null;
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  }

  private async startStream(clubId: string, token: string): Promise<void> {
    try {
      const response = await fetch(`${environment.apiUrl}/api/clubs/${clubId}/feed/stream`, {
        headers: {Authorization: `Bearer ${token}`},
        signal: this.abortController?.signal,
      });

      if (!response.ok || !response.body) return;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const {done, value} = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, {stream: true});
        buffer = buffer.replace(/\r\n/g, '\n');
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';

        for (const raw of events) {
          this.parseEvent(raw);
        }
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
    }

    // Auto-reconnect if still connected to this club
    if (this.currentClubId === clubId) {
      this.reconnectTimeout = setTimeout(() => this.startStream(clubId, token), 3000);
    }
  }

  private parseEvent(raw: string): void {
    const lines = raw.trim().split('\n');
    let eventName = '';
    let dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5));
      }
    }

    if (!eventName || dataLines.length === 0) return;
    const data = dataLines.join('\n');

    try {
      const parsed = JSON.parse(data);
      this.ngZone.run(() => {
        switch (eventName) {
          case 'completion_update':
            this.completionUpdateSubject.next(parsed);
            break;
          case 'new_feed_event':
            this.newFeedEventSubject.next(parsed);
            break;
          case 'kudos_update':
            this.kudosUpdateSubject.next(parsed);
            break;
          case 'comment_update':
            this.commentUpdateSubject.next(parsed);
            break;
          case 'comment_edited':
            this.commentEditedSubject.next(parsed);
            break;
          case 'comment_deleted':
            this.commentDeletedSubject.next(parsed);
            break;
          case 'feed_event_updated':
            this.feedEventUpdatedSubject.next(parsed);
            break;
          case 'feed_event_deleted':
            this.feedEventDeletedSubject.next(parsed);
            break;
          case 'reaction_update':
            this.reactionUpdateSubject.next(parsed);
            break;
          case 'comment_reply_added':
            this.commentReplyAddedSubject.next(parsed);
            break;
        }
      });
    } catch {
      // ignore parse errors (e.g., "connected" heartbeat)
    }
  }
}
