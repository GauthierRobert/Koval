import { User } from './user.model';

export type SportKey = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'TRIATHLON' | 'BRICK' | 'GYM';

export interface SportMeta {
  /** CSS color or var(--…) reference used for sport accents. */
  readonly color: string;
  /** i18n translation key for the sport's display label. */
  readonly labelKey: string;
  /** GPX upload disciplines this sport supports (in display order). */
  readonly gpxDisciplines: readonly string[];
  /** Unit used when rendering distance for this sport. */
  readonly distanceUnit: 'meters' | 'kilometers';
  /**
   * Resolve the athlete's threshold reference value for this sport
   * (FTP for cycling, threshold pace m/s → s/km for running, CSS → s/100m for swimming).
   * Returns `null` when the user has not configured the value.
   */
  resolveThreshold(user: User | null | undefined): number | null;
}

const CYCLING: SportMeta = {
  color: 'var(--sport-cycling)',
  labelKey: 'SPORT.CYCLING',
  gpxDisciplines: ['bike'],
  distanceUnit: 'kilometers',
  resolveThreshold: (user) => user?.ftp ?? null,
};

const RUNNING: SportMeta = {
  color: 'var(--sport-running)',
  labelKey: 'SPORT.RUNNING',
  gpxDisciplines: ['run'],
  distanceUnit: 'kilometers',
  resolveThreshold: (user) => {
    const ftpPace = user?.functionalThresholdPace;
    return ftpPace ? 1000 / ftpPace : null;
  },
};

const SWIMMING: SportMeta = {
  color: 'var(--sport-swimming)',
  labelKey: 'SPORT.SWIMMING',
  gpxDisciplines: ['swim'],
  distanceUnit: 'meters',
  resolveThreshold: (user) => {
    const css = user?.criticalSwimSpeed;
    return css ? 100 / css : null;
  },
};

const TRIATHLON: SportMeta = {
  color: 'var(--sport-triathlon)',
  labelKey: 'SPORT.TRIATHLON',
  gpxDisciplines: ['swim', 'bike', 'run'],
  distanceUnit: 'kilometers',
  resolveThreshold: (user) => user?.ftp ?? null,
};

const BRICK: SportMeta = {
  color: 'var(--accent-color)',
  labelKey: 'SPORT.BRICK',
  gpxDisciplines: ['bike', 'run'],
  distanceUnit: 'kilometers',
  resolveThreshold: (user) => user?.ftp ?? null,
};

const GYM: SportMeta = {
  color: 'var(--text-30)',
  labelKey: 'SPORT.GYM',
  gpxDisciplines: [],
  distanceUnit: 'kilometers',
  resolveThreshold: () => null,
};

export const SPORT_META: Record<SportKey, SportMeta> = {
  CYCLING,
  RUNNING,
  SWIMMING,
  TRIATHLON,
  BRICK,
  GYM,
};

/** Look up sport metadata. Falls back to CYCLING when the key is unknown/empty. */
export function sportMeta(sport: string | null | undefined): SportMeta {
  if (!sport) return SPORT_META.CYCLING;
  const key = sport.toUpperCase() as SportKey;
  return SPORT_META[key] ?? SPORT_META.CYCLING;
}
