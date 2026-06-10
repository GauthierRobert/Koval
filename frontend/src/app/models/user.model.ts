export interface User {
  id: string;
  /**
   * Anonymous public handle (e.g. SwiftOtter-42). Always present on users returned by
   * coach-facing or MCP-shaped endpoints; the user's own /me payload has it too.
   * This is what other coaches/athletes see — real names never leave the backend
   * outside the user's own profile surface.
   */
  alias?: string;
  /**
   * Real display name. Only populated on the current user's own /me payload, NEVER
   * on athletes a coach is viewing or on club member lists — those endpoints scrub
   * it so the same payload is safe over MCP.
   */
  displayName?: string;
  profilePicture?: string;
  role: 'ATHLETE' | 'COACH';
  hasCoach: boolean;
  ftp?: number;
  weightKg?: number;
  functionalThresholdPace?: number;
  criticalSwimSpeed?: number;
  pace5k?: number;
  pace10k?: number;
  paceHalfMarathon?: number;
  paceMarathon?: number;
  vo2maxPower?: number;
  vo2maxPace?: number;
  power3MinW?: number;
  power12MinW?: number;
  /** Derived from power3MinW + power12MinW on the backend; read-only from the client. */
  criticalPower?: number;
  /** Derived from power3MinW + power12MinW on the backend; read-only from the client. */
  wPrimeJ?: number;
  groups?: string[];
  clubs?: string[];
  customZoneReferenceValues?: Record<string, number>;
  ctl?: number;
  atl?: number;
  tsb?: number;
  needsOnboarding?: boolean;
  cguAcceptedAt?: string;
  cguVersion?: string;
  needsCguAcceptance?: boolean;
  aiPrePrompt?: string;
  aiPrePromptEnabled?: boolean;
  linkedAccounts?: {
    strava: boolean;
    google: boolean;
    garmin: boolean;
    polar: boolean;
    suunto: boolean;
    zwift: boolean;
    nolioWrite: boolean;
  };
  authProvider?: string;
  zwiftAutoSyncWorkouts?: boolean;
  suuntoAutoPushWorkouts?: boolean;
  garminAutoPushWorkouts?: boolean;
  nolioAutoSyncWorkouts?: boolean;
}
