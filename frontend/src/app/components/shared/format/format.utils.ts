/**
 * Shared formatting utilities used across multiple components.
 */

/** Format seconds as "M:SS" (no hours) */
export function formatTimeMS(seconds: number | undefined | null): string {
  if (seconds === undefined || seconds === null) return '0:00';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Format seconds as "H:MM:SS" or "M:SS" if under 1 hour */
export function formatTimeHMS(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  return `${m}:${String(s).padStart(2, '0')}`;
}

/** Format seconds as human-readable text: "1h 23m", "45 min" */
export function formatTimeText(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m} min`;
}

/** Format total seconds as pace "M:SS" */
export function formatPace(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = Math.round(totalSeconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Format total seconds as pace, omitting minutes when 0 → "SSs" instead of "0:SS" */
export function formatPaceCompact(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = Math.round(totalSeconds % 60);
  if (m === 0) return `${s}s`;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Format pace with unit based on sport: "4:30/km" or "1:45/100m" */
export function formatPaceWithUnit(secPerUnit: number | null, sport: string): string {
  if (secPerUnit === null) return '\u221E';
  const min = Math.floor(secPerUnit / 60);
  const sec = Math.round(secPerUnit % 60);
  const unit = sport === 'SWIMMING' ? '/100m' : '/km';
  return `${min}:${String(sec).padStart(2, '0')}${unit}`;
}

/** Format training duration: "1h 23m" or "45m", returns "—" for ≤0 */
export function formatTrainingDuration(s: number): string {
  if (s <= 0) return '\u2014';
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

/** Calculate days from today to a target date string (YYYY-MM-DD) */
export function daysUntil(dateStr: string): number {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dateStr + 'T00:00:00');
  return Math.round((target.getTime() - today.getTime()) / 86400000);
}

/** Calculate weeks from today to a target date string (YYYY-MM-DD) */
export function weeksUntil(dateStr: string): number {
  return Math.ceil(daysUntil(dateStr) / 7);
}
