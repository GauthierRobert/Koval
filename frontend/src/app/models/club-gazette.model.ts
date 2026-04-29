import {MediaResponse} from './media.model';

export type GazetteStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export type GazettePostType =
  | 'SESSION_RECAP'
  | 'RACE_RESULT'
  | 'PERSONAL_WIN'
  | 'SHOUTOUT'
  | 'REFLECTION';

export type AutoSection =
  | 'STATS'
  | 'LEADERBOARD'
  | 'TOP_SESSIONS'
  | 'MILESTONES'
  | 'MOST_ACTIVE_MEMBERS';

export interface WeeklyStatsSnapshot {
  swimKm: number;
  bikeKm: number;
  runKm: number;
  sessionCount: number;
  totalHours: number;
  totalTss: number;
  memberCount: number;
  clubSessionsCount: number;
  attendanceRate: number;
}

export interface LeaderboardSnapshot {
  rank: number;
  userId: string;
  displayName: string;
  profilePicture: string | null;
  tss: number;
  sessionCount: number;
}

export interface TopSessionSnapshot {
  clubSessionId: string;
  title: string;
  sport: string;
  date: string | null;
  participantCount: number;
  participantNames: string[];
}

export interface MemberHighlightSnapshot {
  userId: string;
  displayName: string;
  profilePicture: string | null;
  hours: number;
  sessions: number;
  tss: number;
}

export interface MilestoneSnapshot {
  type: string;
  userId: string;
  displayName: string;
  profilePicture: string | null;
  description: string;
}

export interface CommentEntry {
  id: string;
  userId: string;
  displayName: string;
  profilePicture: string | null;
  content: string;
  createdAt: string;
}

export interface ClubGazetteEditionSummary {
  id: string;
  editionNumber: number;
  periodStart: string;
  periodEnd: string;
  status: GazetteStatus;
  publishedAt: string | null;
  hasPdf: boolean;
}

export interface ClubGazetteEditionResponse {
  id: string;
  clubId: string;
  editionNumber: number;
  periodStart: string;
  periodEnd: string;
  status: GazetteStatus;
  publishedAt: string | null;
  publishedByUserId: string | null;
  includedSections: AutoSection[];
  statsSnapshot: WeeklyStatsSnapshot | null;
  leaderboardSnapshot: LeaderboardSnapshot[];
  topSessions: TopSessionSnapshot[];
  mostActiveMembers: MemberHighlightSnapshot[];
  milestones: MilestoneSnapshot[];
  viewCount: number;
  comments: CommentEntry[];
  hasPdf: boolean;
}

export interface LinkedSessionSnapshot {
  sessionId: string;
  title: string;
  sport: string;
  scheduledAt: string;
  location: string | null;
}

export interface LinkedRaceGoalSnapshot {
  raceGoalId: string;
  title: string;
  sport: string;
  raceDate: string;
  distance: string | null;
  targetTime: string | null;
  finishTime: string | null;
}

export interface ClubGazettePostResponse {
  id: string;
  editionId: string;
  authorId: string;
  authorDisplayName: string;
  authorProfilePicture: string | null;
  type: GazettePostType;
  title: string | null;
  content: string;
  linkedSessionId: string | null;
  linkedRaceGoalId: string | null;
  linkedSessionSnapshot: LinkedSessionSnapshot | null;
  linkedRaceGoalSnapshot: LinkedRaceGoalSnapshot | null;
  photos: MediaResponse[];
  displayOrder: number | null;
  excluded: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ClubGazettePostsResponse {
  posts: ClubGazettePostResponse[];
  othersDraftCount: number;
}

export interface CreateGazettePostRequest {
  type: GazettePostType;
  title?: string | null;
  content: string;
  linkedSessionId?: string | null;
  linkedRaceGoalId?: string | null;
  mediaIds?: string[];
}

export interface UpdateGazettePostRequest {
  title?: string | null;
  content?: string | null;
  linkedSessionId?: string | null;
  linkedRaceGoalId?: string | null;
  mediaIds?: string[];
}
