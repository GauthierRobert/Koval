import {describe, expect, it} from 'vitest';
import {ClubSessionParticipationService} from './club-session-participation.service';
import {ClubTrainingSession} from '../../models/club.model';

const service = new ClubSessionParticipationService();

function makeSession(over: Partial<ClubTrainingSession> = {}): ClubTrainingSession {
  return {
    id: 's1',
    clubId: 'c1',
    createdBy: 'u-coach',
    title: 'Track session',
    participantIds: [],
    createdAt: '2025-01-01T00:00:00Z',
    ...over,
  };
}

describe('ClubSessionParticipationService', () => {
  it('isJoined returns false when userId is missing', () => {
    expect(service.isJoined(makeSession({participantIds: ['u1']}), null)).toBe(false);
    expect(service.isJoined(makeSession({participantIds: ['u1']}), undefined)).toBe(false);
  });

  it('isJoined matches participantIds', () => {
    expect(service.isJoined(makeSession({participantIds: ['u1', 'u2']}), 'u1')).toBe(true);
    expect(service.isJoined(makeSession({participantIds: ['u1', 'u2']}), 'u3')).toBe(false);
  });

  it('isFull is true only when maxParticipants is set and reached', () => {
    expect(service.isFull(makeSession({participantIds: ['u1']}))).toBe(false);
    expect(service.isFull(makeSession({participantIds: ['u1'], maxParticipants: 1}))).toBe(true);
    expect(service.isFull(makeSession({participantIds: [], maxParticipants: 1}))).toBe(false);
  });

  it('isOnWaitingList checks the waitingList array', () => {
    const session = makeSession({
      waitingList: [
        {userId: 'u1', joinedAt: '2025-01-01T00:00:00Z'},
        {userId: 'u2', joinedAt: '2025-01-01T00:01:00Z'},
      ],
    });
    expect(service.isOnWaitingList(session, 'u1')).toBe(true);
    expect(service.isOnWaitingList(session, 'u3')).toBe(false);
    expect(service.isOnWaitingList(session, null)).toBe(false);
  });

  it('getWaitingListPosition returns 1-based index', () => {
    const session = makeSession({
      waitingList: [
        {userId: 'u1', joinedAt: '2025-01-01T00:00:00Z'},
        {userId: 'u2', joinedAt: '2025-01-01T00:01:00Z'},
      ],
    });
    expect(service.getWaitingListPosition(session, 'u1')).toBe(1);
    expect(service.getWaitingListPosition(session, 'u2')).toBe(2);
    expect(service.getWaitingListPosition(session, 'u3')).toBe(0);
    expect(service.getWaitingListPosition(session, null)).toBe(0);
  });

  it('getParticipation returns the combined view', () => {
    const session = makeSession({
      participantIds: ['u-other'],
      maxParticipants: 1,
      waitingList: [{userId: 'u1', joinedAt: 't'}],
    });
    expect(service.getParticipation(session, 'u1')).toEqual({
      joined: false,
      onWaitingList: true,
      waitingListPosition: 1,
      full: true,
    });
  });

  it('getParticipation returns an empty-ish view with computed full flag when no user', () => {
    const session = makeSession({participantIds: ['u-other'], maxParticipants: 1});
    expect(service.getParticipation(session, null)).toEqual({
      joined: false,
      onWaitingList: false,
      waitingListPosition: 0,
      full: true,
    });
  });
});
