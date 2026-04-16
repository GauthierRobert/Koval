export type ClubVisibility = 'PUBLIC' | 'PRIVATE';
export type ClubMemberRole = 'OWNER' | 'ADMIN' | 'COACH' | 'MEMBER';
export type ClubActivityType =
  | 'MEMBER_JOINED'
  | 'MEMBER_LEFT'
  | 'SESSION_CREATED'
  | 'SESSION_JOINED'
  | 'SESSION_CANCELLED'
  | 'RECURRING_SERIES_CANCELLED'
  | 'TRAINING_CREATED'
  | 'RACE_GOAL_ADDED'
  | 'WAITING_LIST_JOINED';

export interface ClubMembership {
  id: string;
  clubId: string;
  userId: string;
  role: ClubMemberRole;
  status: string;
  joinedAt?: string;
}

export interface ClubSummary {
  id: string;
  name: string;
  description?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
  membershipStatus?: string;
}

export interface ClubDetail {
  id: string;
  name: string;
  description?: string;
  location?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
  memberCount: number;
  ownerId: string;
  currentMembershipStatus?: string;
  currentMemberRole?: ClubMemberRole;
  createdAt: string;
}

export interface ClubMember {
  membershipId?: string;
  userId: string;
  displayName: string;
  profilePicture?: string;
  role: ClubMemberRole;
  joinedAt: string;
  tags?: string[];
}

export interface ClubGroup {
  id: string;
  clubId: string;
  name: string;
  memberIds: string[];
}

export interface MyClubRoleEntry {
  clubId: string;
  clubName: string;
  role: ClubMemberRole;
}

export interface WaitingListEntry {
  userId: string;
  joinedAt: string;
}

export interface GroupLinkedTraining {
  clubGroupId?: string;
  clubGroupName?: string;
  trainingId: string;
  trainingTitle?: string;
  trainingDescription?: string;
}

export function getEffectiveLinkedTrainings(session: ClubTrainingSession): GroupLinkedTraining[] {
  if (session.linkedTrainings && session.linkedTrainings.length > 0) {
    return session.linkedTrainings;
  }
  if (session.linkedTrainingId) {
    return [
      {
        trainingId: session.linkedTrainingId,
        trainingTitle: session.linkedTrainingTitle,
        trainingDescription: session.linkedTrainingDescription,
      },
    ];
  }
  return [];
}

export interface ClubTrainingSession {
  id: string;
  clubId: string;
  createdBy: string;
  title: string;
  sport?: string;
  scheduledAt?: string;
  location?: string;
  meetingPointLat?: number;
  meetingPointLon?: number;
  description?: string;
  linkedTrainingId?: string;
  linkedTrainings?: GroupLinkedTraining[];
  participantIds: string[];
  createdAt: string;
  recurringTemplateId?: string;
  clubGroupId?: string;
  responsibleCoachId?: string;
  maxParticipants?: number;
  durationMinutes?: number;
  linkedTrainingTitle?: string;
  linkedTrainingDescription?: string;
  waitingList?: WaitingListEntry[];
  openToAll?: boolean;
  openToAllDelayValue?: number;
  openToAllDelayUnit?: 'HOURS' | 'DAYS';
  cancelled?: boolean;
  cancellationReason?: string;
  cancelledAt?: string;
  category?: 'SCHEDULED' | 'OPEN';
  gpxFileName?: string;
  routeCoordinates?: { lat: number; lon: number; elevation: number; distance: number }[];
}

export interface RecurringSessionTemplate {
  id: string;
  clubId: string;
  createdBy: string;
  title: string;
  sport?: string;
  dayOfWeek: string;
  timeOfDay: string;
  location?: string;
  meetingPointLat?: number;
  meetingPointLon?: number;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
  clubGroupId?: string;
  responsibleCoachId?: string;
  openToAll?: boolean;
  openToAllDelayValue?: number;
  openToAllDelayUnit?: 'HOURS' | 'DAYS';
  endDate?: string;
  active: boolean;
  createdAt: string;
  category?: 'SCHEDULED' | 'OPEN';
  gpxFileName?: string;
  routeCoordinates?: { lat: number; lon: number; elevation: number; distance: number }[];
}

export interface ClubActivity {
  id: string;
  type: ClubActivityType;
  actorId: string;
  actorName?: string;
  targetId?: string;
  targetTitle?: string;
  occurredAt: string;
}

export interface ClubWeeklyStats {
  totalSwimKm: number;
  totalBikeKm: number;
  totalRunKm: number;
  totalSessions: number;
  memberCount: number;
}

export interface WeekAttendanceEntry {
  weekLabel: string;
  sessionId: string | null;
  cancelled: boolean;
  participantCount: number;
  eligibleCount: number;
  fillPercent: number;
}

export interface AthletePresenceEntry {
  userId: string;
  displayName: string;
  profilePicture: string | null;
  weekPresence: (boolean | null)[];
}

export interface RecurringTemplateAttendance {
  templateId: string;
  templateTitle: string;
  sport: string;
  dayOfWeek: string;
  timeOfDay: string;
  clubGroupId: string | null;
  clubGroupName: string | null;
  maxParticipants: number | null;
  eligibleCount: number;
  weeks: WeekAttendanceEntry[];
  athleteGrid: AthletePresenceEntry[];
}

export interface WeeklyTrend {
  weekLabel: string;
  totalTss: number;
  totalHours: number;
  sessionCount: number;
  attendanceRate: number;
}

export interface MemberHighlight {
  userId: string;
  displayName: string;
  profilePicture?: string;
  totalHours: number;
  sessionCount: number;
  totalTss: number;
}

export interface ClubExtendedStats {
  totalSwimKm: number;
  totalBikeKm: number;
  totalRunKm: number;
  totalSessions: number;
  memberCount: number;
  totalDurationHours: number;
  totalTss: number;
  attendanceRate: number;
  clubSessionsThisWeek: number;
  recurringAttendance: RecurringTemplateAttendance[];
  sportDistribution: Record<string, number>;
  avgTssPerMember: number;
  weeklyTrends: WeeklyTrend[];
  mostActiveMembers: MemberHighlight[];
}

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  profilePicture?: string;
  weeklyTss: number;
  sessionCount: number;
  rank: number;
}

export interface ClubRaceGoalResponse {
  raceId?: string;
  title: string;
  sport: string;
  raceDate: string;
  distance?: string;
  location?: string;
  participants: {
    userId: string;
    displayName: string;
    profilePicture: string | null;
    priority: string;
    targetTime?: string;
  }[];
}

export interface ClubInviteCode {
  id: string;
  code: string;
  clubId: string;
  createdBy: string;
  clubGroupId?: string;
  clubGroupName?: string;
  maxUses: number;
  currentUses: number;
  expiresAt?: string;
  active: boolean;
  createdAt: string;
}

// --- Feed Event Types ---
export type ClubFeedEventType = 'SESSION_COMPLETION' | 'RACE_COMPLETION' | 'COACH_ANNOUNCEMENT' | 'NEXT_GOAL';

export interface CompletionEntry {
  userId: string;
  displayName: string;
  profilePicture?: string;
  completedSessionId: string;
  stravaActivityId?: string;
  completedAt: string;
}

export interface EngagedAthlete {
  userId: string;
  displayName: string;
  profilePicture?: string;
  priority: string;
  targetTime?: string;
}

export interface RaceCompletionEntry {
  userId: string;
  displayName: string;
  profilePicture?: string;
  finishTime?: string;
  stravaActivityId?: string;
}

export interface FeedCommentEntry {
  id: string;
  userId: string;
  displayName: string;
  profilePicture?: string;
  content: string;
  createdAt: string;
}

export interface ClubFeedEventResponse {
  id: string;
  type: ClubFeedEventType;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
  // SESSION_COMPLETION
  clubSessionId?: string;
  sessionTitle?: string;
  sessionSport?: string;
  sessionScheduledAt?: string;
  completions?: CompletionEntry[];
  kudosGivenBy?: string[];
  // RACE_COMPLETION
  raceGoalId?: string;
  raceTitle?: string;
  raceDate?: string;
  raceCompletions?: RaceCompletionEntry[];
  // COACH_ANNOUNCEMENT
  authorId?: string;
  authorName?: string;
  authorProfilePicture?: string;
  announcementContent?: string;
  // NEXT_GOAL
  goalTitle?: string;
  goalSport?: string;
  goalDate?: string;
  goalLocation?: string;
  engagedAthletes?: EngagedAthlete[];
  // COMMENTS
  comments?: FeedCommentEntry[];
}

export interface ClubFeedResponse {
  pinned: ClubFeedEventResponse[];
  items: ClubFeedEventResponse[];
  page: number;
  hasMore: boolean;
}

export interface KudosResponse {
  results: { athleteName: string; stravaActivityId: string; success: boolean; error?: string }[];
  successCount: number;
  failCount: number;
}

export interface CompletionUpdatePayload {
  feedEventId: string;
  clubSessionId: string;
  completionCount: number;
  latestCompletion: { userId: string; displayName: string; profilePicture?: string };
}

export interface CommentUpdatePayload {
  feedEventId: string;
  comment: FeedCommentEntry;
}

export interface CreateClubData {
  name: string;
  description?: string;
  location?: string;
  logoUrl?: string;
  visibility: ClubVisibility;
}

export interface CreateSessionData {
  category?: 'SCHEDULED' | 'OPEN';
  title: string;
  sport?: string;
  scheduledAt?: string;
  location?: string;
  meetingPointLat?: number;
  meetingPointLon?: number;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
  durationMinutes?: number;
  clubGroupId?: string;
  responsibleCoachId?: string;
  openToAll?: boolean;
  openToAllDelayValue?: number;
  openToAllDelayUnit?: 'HOURS' | 'DAYS';
}

export interface CreateRecurringSessionData {
  category?: 'SCHEDULED' | 'OPEN';
  title: string;
  sport?: string;
  dayOfWeek: string;
  timeOfDay: string;
  location?: string;
  meetingPointLat?: number;
  meetingPointLon?: number;
  description?: string;
  linkedTrainingId?: string;
  maxParticipants?: number;
  clubGroupId?: string;
  responsibleCoachId?: string;
  openToAll?: boolean;
  openToAllDelayValue?: number;
  openToAllDelayUnit?: 'HOURS' | 'DAYS';
  endDate?: string;
}
