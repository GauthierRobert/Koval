import {DistanceCategory, PageResponse, Race} from '../../../services/race.service';

/** Returns a new page with the race replaced in-place; same reference if not found. */
export function replaceRaceInPage(page: PageResponse<Race>, updated: Race): PageResponse<Race> {
  const idx = page.content.findIndex(r => r.id === updated.id);
  if (idx === -1) return page;
  const content = [...page.content];
  content[idx] = updated;
  return {...page, content};
}

export interface RacesDistancePreset {
  value: DistanceCategory;
  label: string;
}

export const DISTANCE_PRESETS_BY_SPORT: Record<string, RacesDistancePreset[]> = {
  TRIATHLON: [
    {value: 'TRI_PROMO', label: 'Promo'},
    {value: 'TRI_SUPER_SPRINT', label: 'Super Sprint'},
    {value: 'TRI_SPRINT', label: 'Sprint'},
    {value: 'TRI_OLYMPIC', label: 'Olympic'},
    {value: 'TRI_HALF', label: '70.3'},
    {value: 'TRI_IRONMAN', label: 'Ironman'},
    {value: 'TRI_ULTRA', label: 'Ultra'},
    {value: 'TRI_AQUATHLON', label: 'Aquathlon'},
    {value: 'TRI_DUATHLON', label: 'Duathlon'},
    {value: 'TRI_AQUABIKE', label: 'Aquabike'},
    {value: 'TRI_CROSS', label: 'Cross / XTERRA'},
  ],
  RUNNING: [
    {value: 'RUN_5K', label: '5K'},
    {value: 'RUN_10K', label: '10K'},
    {value: 'RUN_HALF_MARATHON', label: 'Semi'},
    {value: 'RUN_MARATHON', label: 'Marathon'},
    {value: 'RUN_ULTRA', label: 'Ultra'},
  ],
  CYCLING: [
    {value: 'BIKE_GRAN_FONDO', label: 'Gran Fondo'},
    {value: 'BIKE_MEDIO_FONDO', label: 'Medio'},
    {value: 'BIKE_TT', label: 'TT'},
    {value: 'BIKE_ULTRA', label: 'Ultra'},
  ],
  SWIMMING: [
    {value: 'SWIM_1500M', label: '1.5K'},
    {value: 'SWIM_5K', label: '5K'},
    {value: 'SWIM_10K', label: '10K'},
    {value: 'SWIM_MARATHON', label: 'Marathon'},
    {value: 'SWIM_ULTRA', label: 'Ultra'},
  ],
  OTHER: [],
};

const MONTH_ABBR: Record<string, string> = {
  '01': 'JAN', '02': 'FEV', '03': 'MAR', '04': 'AVR', '05': 'MAI', '06': 'JUN',
  '07': 'JUL', '08': 'AOU', '09': 'SEP', '10': 'OCT', '11': 'NOV', '12': 'DEC',
};

export function monthAbbr(iso?: string): string {
  return iso && iso.length >= 7 ? MONTH_ABBR[iso.slice(5, 7)] ?? '—' : '—';
}

export function domDay(iso?: string): string {
  if (!iso || iso.length < 10) return '–';
  return parseInt(iso.slice(8, 10), 10).toString();
}

export function shortYear(iso?: string): string {
  if (!iso || iso.length < 4) return '';
  return iso.slice(2, 4);
}

export function daysUntil(iso?: string): number | null {
  if (!iso) return null;
  const target = new Date(iso + 'T00:00:00').getTime();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  return Math.round((target - today.getTime()) / (1000 * 60 * 60 * 24));
}

export function isPast(race: Race): boolean {
  const d = daysUntil(race.scheduledDate);
  return d !== null && d < 0;
}

export function formatKm(meters?: number): string {
  if (!meters) return '';
  return (meters / 1000).toFixed(1);
}

export function totalDistanceKm(race: Race): string {
  const total = ((race.swimDistanceM ?? 0) + (race.bikeDistanceM ?? 0) + (race.runDistanceM ?? 0)) / 1000;
  return total > 0 ? total.toFixed(1) : '—';
}

export function countryFlag(code?: string): string {
  if (!code || code.length !== 2) return '';
  const A = 0x1F1E6;
  return String.fromCodePoint(A + code.toUpperCase().charCodeAt(0) - 65) +
    String.fromCodePoint(A + code.toUpperCase().charCodeAt(1) - 65);
}

export function getGpxDisciplines(race: Race): string[] {
  switch (race.sport?.toUpperCase()) {
    case 'TRIATHLON': return ['swim', 'bike', 'run'];
    case 'CYCLING': return ['bike'];
    case 'RUNNING': return ['run'];
    case 'SWIMMING': return ['swim'];
    default: return ['bike'];
  }
}

export function hasGpx(race: Race, discipline: string): boolean {
  switch (discipline) {
    case 'swim': return !!race.hasSwimGpx;
    case 'bike': return !!race.hasBikeGpx;
    case 'run': return !!race.hasRunGpx;
    default: return false;
  }
}

export function gpxCount(race: Race): number {
  return getGpxDisciplines(race).filter(d => hasGpx(race, d)).length;
}

export function gpxTotal(race: Race): number {
  return getGpxDisciplines(race).length;
}

export function gpxRingDashArray(race: Race, circumference: number): string {
  const pct = gpxCount(race) / gpxTotal(race);
  return `${circumference * pct} ${circumference}`;
}

export function getDisciplineDistanceKm(race: Race, discipline: string): number | null {
  const m = discipline === 'swim' ? race.swimDistanceM
    : discipline === 'bike' ? race.bikeDistanceM
      : discipline === 'run' ? race.runDistanceM : null;
  return m ? m / 1000 : null;
}

export function getLoopCount(race: Race, discipline: string): number {
  switch (discipline) {
    case 'swim': return race.swimGpxLoops ?? 1;
    case 'bike': return race.bikeGpxLoops ?? 1;
    case 'run': return race.runGpxLoops ?? 1;
    default: return 1;
  }
}

export function canSimulate(race: Race): boolean {
  switch (race.sport?.toUpperCase()) {
    case 'CYCLING': return !!race.hasBikeGpx;
    case 'RUNNING': return !!race.hasRunGpx;
    case 'TRIATHLON': return !!race.hasBikeGpx && !!race.hasRunGpx;
    case 'SWIMMING': return true;
    default: return !!race.hasBikeGpx;
  }
}

export type DatePreset = '12m' | 'y2026' | 'y2027';
export type VerifiedFilter = 'all' | 'verified' | 'community';

export interface RaceFilterCriteria {
  query: string;
  distances: ReadonlySet<DistanceCategory>;
  verified: VerifiedFilter;
  datePreset: DatePreset;
}

export function applyRaceFilters(races: Race[], criteria: RaceFilterCriteria): Race[] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const in12Months = new Date(today);
  in12Months.setMonth(in12Months.getMonth() + 12);

  return races.filter(r => {
    if (criteria.distances.size > 0) {
      if (!r.distanceCategory || !criteria.distances.has(r.distanceCategory)) return false;
    }
    if (criteria.verified === 'verified' && !r.verified) return false;
    if (criteria.verified === 'community' && r.verified) return false;
    if (criteria.query) {
      const haystack = [r.title, r.location, r.country, r.distance].join(' ').toLowerCase();
      if (!haystack.includes(criteria.query)) return false;
    }
    if (!r.scheduledDate) return false;
    if (criteria.datePreset === '12m') {
      const t = new Date(r.scheduledDate + 'T00:00:00').getTime();
      if (t < today.getTime() || t > in12Months.getTime()) return false;
    } else if (criteria.datePreset === 'y2026') {
      if (r.scheduledDate.slice(0, 4) !== '2026') return false;
    } else if (criteria.datePreset === 'y2027') {
      if (r.scheduledDate.slice(0, 4) !== '2027') return false;
    }
    return true;
  });
}

/** Builds the "missing GPX" label for a race that can't be simulated yet. */
export function missingGpxDisciplines(race: Race): string[] {
  const missing: string[] = [];
  const sport = race.sport?.toUpperCase();
  if (sport === 'CYCLING' || sport === 'TRIATHLON' || !sport || !['RUNNING', 'SWIMMING'].includes(sport)) {
    if (!race.hasBikeGpx) missing.push('Bike');
  }
  if (sport === 'RUNNING' || sport === 'TRIATHLON') {
    if (!race.hasRunGpx) missing.push('Run');
  }
  return missing;
}

/** Copies all the editable race fields into a plain object for two-way form binding. */
export function buildEditFormFromRace(r: Race): Partial<Race> {
  return {
    title: r.title,
    sport: r.sport,
    location: r.location ?? '',
    country: r.country ?? '',
    distance: r.distance ?? '',
    distanceCategory: r.distanceCategory,
    swimDistanceM: r.swimDistanceM,
    bikeDistanceM: r.bikeDistanceM,
    runDistanceM: r.runDistanceM,
    elevationGainM: r.elevationGainM,
    description: r.description ?? '',
    website: r.website ?? '',
    scheduledDate: r.scheduledDate ?? '',
  };
}
