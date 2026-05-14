import { PmcDataPoint } from '../../../services/metrics.service';
import { SavedSession } from '../../../services/history.service';
import { SessionData } from '../../../models/session-types.model';
import { formatWeekRangeDash, getMonday, getSunday } from '../../../utils/date.utils';

export const PMC_WINDOW_DAYS = 90;
export const VOLUME_WINDOW_DAYS = 70;
export const PROJECTION_DAYS = 30;

export interface AthleteMetrics {
  ctl: number;
  atl: number;
  tsb: number;
  ctlTrend: number;
  atlTrend: number;
}

export { toDateKey, dateOffsetKey } from '../../../utils/date.utils';

/** @deprecated Use {@link getMonday} from `utils/date.utils`. */
export const getMondayOfWeek = getMonday;
/** @deprecated Use {@link getSunday} from `utils/date.utils`. */
export const getSundayOfWeek = getSunday;
/** @deprecated Use {@link formatWeekRangeDash} from `utils/date.utils`. */
export const formatScheduleWeekLabel = formatWeekRangeDash;

export function deriveAthleteMetrics(data: PmcDataPoint[]): AthleteMetrics | null {
  if (!data.length) return null;
  const real = data.filter((d) => !d.predicted);
  if (!real.length) return null;
  const latest = real[real.length - 1];
  const tenDaysAgo = real.length > 10 ? real[real.length - 11] : null;
  return {
    ctl: latest?.ctl ?? 0,
    atl: latest?.atl ?? 0,
    tsb: latest?.tsb ?? 0,
    ctlTrend: tenDaysAgo ? latest.ctl - tenDaysAgo.ctl : 0,
    atlTrend: tenDaysAgo ? latest.atl - tenDaysAgo.atl : 0,
  };
}

export function buildProjectionTssMap(
  workouts: ReadonlyArray<{ status?: string; tss?: number; scheduledDate: string }>,
): Map<string, number> {
  const m = new Map<string, number>();
  for (const w of workouts) {
    if (w.status === 'PENDING' && w.tss) {
      m.set(w.scheduledDate, (m.get(w.scheduledDate) ?? 0) + w.tss);
    }
  }
  return m;
}

interface RawSessionDetail {
  id: string;
  title: string;
  totalDurationSeconds: number;
  avgPower: number;
  avgHR: number;
  avgCadence: number;
  avgSpeed?: number;
  blockSummaries?: SavedSession['blockSummaries'];
  sportType: SavedSession['sportType'];
  completedAt: string | number | Date;
  tss?: number | null;
  intensityFactor?: number | null;
  fitFileId?: string | null;
  stravaActivityId?: string | null;
}

export function mapSessionToSavedSession(s: RawSessionDetail): SavedSession {
  return {
    id: s.id,
    title: s.title,
    totalDuration: s.totalDurationSeconds,
    avgPower: s.avgPower,
    avgHR: s.avgHR,
    avgCadence: s.avgCadence,
    avgSpeed: s.avgSpeed || 0,
    blockSummaries: s.blockSummaries || [],
    history: [],
    sportType: s.sportType,
    date: new Date(s.completedAt),
    syncedToStrava: false,
    syncedToGarmin: false,
    tss: s.tss ?? undefined,
    intensityFactor: s.intensityFactor ?? undefined,
    fitFileId: s.fitFileId ?? undefined,
    stravaActivityId: s.stravaActivityId ?? undefined,
  };
}

/** Filters athletes by tag and club selections (null = no filter). */
export function filterAthletes<T extends { groups?: string[]; clubs?: string[] }>(
  athletes: T[],
  tagFilter: string | null,
  clubFilter: string | null,
): T[] {
  let result = athletes;
  if (tagFilter) result = result.filter((a) => a.groups?.includes(tagFilter));
  if (clubFilter) result = result.filter((a) => a.clubs?.includes(clubFilter));
  return result;
}

export type { SessionData };
