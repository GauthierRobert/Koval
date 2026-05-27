import { PmcDataPoint } from './metrics.service';

export function computeIF(avgPower: number, ftp: number): number {
  if (!ftp || ftp <= 0 || !avgPower) return 0;
  return avgPower / ftp;
}

export function computeTss(durationSeconds: number, avgPower: number, ftp: number): number {
  if (!ftp || ftp <= 0 || !avgPower || !durationSeconds) return 0;
  const intensityFactor = computeIF(avgPower, ftp);
  return (durationSeconds / 3600) * intensityFactor * intensityFactor * 100;
}

/**
 * Coggan/Allen RPE→IF lookup. Mirrors {@code TssCalculator.intensityFactorFromRpe}
 * on the backend so optimistic UI matches the persisted value.
 */
const RPE_TO_IF: readonly number[] = [0.5, 0.55, 0.6, 0.65, 0.75, 0.8, 0.85, 0.9, 0.95, 1.05];

export function intensityFactorFromRpe(rpe: number): number {
  const clamped = Math.max(1, Math.min(10, Math.round(rpe)));
  return RPE_TO_IF[clamped - 1];
}

export function computeTssFromRpe(durationSeconds: number, rpe: number): number {
  if (!durationSeconds || !rpe) return 0;
  const intensity = intensityFactorFromRpe(rpe);
  return (durationSeconds / 3600) * intensity * intensity * 100;
}

export function projectPmc(
  realData: PmcDataPoint[],
  dailyTss: number,
  days: number,
): PmcDataPoint[] {
  if (!realData.length) return [];
  const last = realData[realData.length - 1];
  const kCTL = 1 - Math.exp(-1 / 42);
  const kATL = 1 - Math.exp(-1 / 7);

  let ctl = last.ctl;
  let atl = last.atl;
  const lastDate = new Date(last.date);
  const result: PmcDataPoint[] = [];

  for (let i = 1; i <= days; i++) {
    ctl = ctl + (dailyTss - ctl) * kCTL;
    atl = atl + (dailyTss - atl) * kATL;
    const d = new Date(lastDate);
    d.setDate(d.getDate() + i);
    result.push({
      date: d.toISOString().split('T')[0],
      ctl: Math.round(ctl * 10) / 10,
      atl: Math.round(atl * 10) / 10,
      tsb: Math.round((ctl - atl) * 10) / 10,
      dailyTss,
      predicted: true,
    });
  }
  return result;
}

export function findPeakForm(data: PmcDataPoint[]): { date: string; tsb: number } | null {
  if (!data.length) return null;
  return data.reduce((best, d) => (d.tsb > best.tsb ? d : best), data[0]);
}

/**
 * Projects future PMC from scheduled workouts. The map carries the per-sport
 * TSS breakdown for each day so the projected TSS bars can be drawn in sport
 * colours (matching the historical bars), not a single anonymous total.
 */
export function projectPmcFromSchedule(
  realData: PmcDataPoint[],
  scheduledTss: Map<string, Record<string, number>>,
  days: number,
): PmcDataPoint[] {
  if (!realData.length) return [];
  const last = realData[realData.length - 1];
  const kCTL = 1 - Math.exp(-1 / 42);
  const kATL = 1 - Math.exp(-1 / 7);
  let ctl = last.ctl;
  let atl = last.atl;
  const lastDate = new Date(last.date);
  const result: PmcDataPoint[] = [];

  for (let i = 1; i <= days; i++) {
    const d = new Date(lastDate);
    d.setDate(d.getDate() + i);
    const dateStr = d.toISOString().split('T')[0];
    const sportTss = scheduledTss.get(dateStr);
    const dayTss = sportTss ? Object.values(sportTss).reduce((s, v) => s + v, 0) : 0;
    ctl = ctl + (dayTss - ctl) * kCTL;
    atl = atl + (dayTss - atl) * kATL;
    result.push({
      date: dateStr,
      ctl: Math.round(ctl * 10) / 10,
      atl: Math.round(atl * 10) / 10,
      tsb: Math.round((ctl - atl) * 10) / 10,
      dailyTss: dayTss,
      sportTss: sportTss ? { ...sportTss } : undefined,
      predicted: true,
    });
  }
  return result;
}
