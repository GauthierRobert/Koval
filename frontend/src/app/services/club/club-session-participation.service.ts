import {Injectable} from '@angular/core';
import {ClubTrainingSession} from '../../models/club.model';

export interface SessionParticipation {
  joined: boolean;
  onWaitingList: boolean;
  /** 1-based position on the waiting list, or 0 when not waiting. */
  waitingListPosition: number;
  full: boolean;
}

const EMPTY: SessionParticipation = {
  joined: false,
  onWaitingList: false,
  waitingListPosition: 0,
  full: false,
};

@Injectable({providedIn: 'root'})
export class ClubSessionParticipationService {
  isJoined(session: ClubTrainingSession, userId: string | null | undefined): boolean {
    return !!userId && session.participantIds.includes(userId);
  }

  isFull(session: ClubTrainingSession): boolean {
    return (
      session.maxParticipants != null &&
      session.participantIds.length >= session.maxParticipants
    );
  }

  isOnWaitingList(
    session: ClubTrainingSession,
    userId: string | null | undefined,
  ): boolean {
    return !!userId && !!session.waitingList?.some((e) => e.userId === userId);
  }

  getWaitingListPosition(
    session: ClubTrainingSession,
    userId: string | null | undefined,
  ): number {
    if (!userId || !session.waitingList) return 0;
    const idx = session.waitingList.findIndex((e) => e.userId === userId);
    return idx >= 0 ? idx + 1 : 0;
  }

  /** Combined view used to feed a session-card child component in one input. */
  getParticipation(
    session: ClubTrainingSession,
    userId: string | null | undefined,
  ): SessionParticipation {
    if (!userId) return {...EMPTY, full: this.isFull(session)};
    return {
      joined: this.isJoined(session, userId),
      onWaitingList: this.isOnWaitingList(session, userId),
      waitingListPosition: this.getWaitingListPosition(session, userId),
      full: this.isFull(session),
    };
  }
}
