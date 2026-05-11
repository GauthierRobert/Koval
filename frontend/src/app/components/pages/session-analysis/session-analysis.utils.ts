import {FitLap, FitRecord, FitTimerEvent} from '../../../services/metrics.service';
import {BlockSummary} from '../../../services/workout-execution.service';

/** 0m laps = passive rest; active laps carry distance and a swim_stroke. */
export function lapsToBlockSummaries(laps: FitLap[]): BlockSummary[] {
  let swimIndex = 0;
  return laps.map(lap => {
    const isRest = lap.totalDistanceMeters === 0;
    if (isRest) {
      return {
        label: 'Rest',
        durationSeconds: lap.totalTimerSeconds,
        distanceMeters: 0,
        targetPower: 0,
        actualPower: 0,
        actualCadence: 0,
        actualHR: lap.avgHeartRate,
        type: 'REST',
      };
    }
    swimIndex++;
    const stroke = lap.swimStroke
      ? lap.swimStroke.charAt(0).toUpperCase() + lap.swimStroke.slice(1)
      : '';
    return {
      label: `Lap ${swimIndex}${stroke ? ' · ' + stroke : ''}`,
      durationSeconds: lap.totalTimerSeconds,
      distanceMeters: lap.totalDistanceMeters,
      targetPower: 0,
      actualPower: lap.avgPower,
      actualCadence: lap.avgCadence,
      actualHR: lap.avgHeartRate,
      type: 'INTERVAL',
    };
  });
}

/**
 * Strip paused periods from records using FIT timer events (stop/start pairs).
 * Falls back to gap-based detection if no timer events are available.
 */
export function stripPauses(records: FitRecord[], timerEvents: FitTimerEvent[]): {records: FitRecord[]; movingTime: number} {
  if (records.length < 2) return {records, movingTime: 0};

  const pauseRanges: {start: number; end: number}[] = [];
  for (let i = 0; i < timerEvents.length; i++) {
    if (timerEvents[i].type === 'stop') {
      for (let j = i + 1; j < timerEvents.length; j++) {
        if (timerEvents[j].type === 'start') {
          pauseRanges.push({start: timerEvents[i].timestamp, end: timerEvents[j].timestamp});
          i = j;
          break;
        }
      }
    }
  }

  if (pauseRanges.length === 0) return stripPausesByGap(records);

  const cumulativePause = new Array<number>(pauseRanges.length);
  let cumPause = 0;
  for (let i = 0; i < pauseRanges.length; i++) {
    cumPause += pauseRanges[i].end - pauseRanges[i].start;
    cumulativePause[i] = cumPause;
  }

  const result: FitRecord[] = [];
  let pauseIdx = 0;
  for (const record of records) {
    while (pauseIdx < pauseRanges.length && pauseRanges[pauseIdx].end <= record.timestamp) {
      pauseIdx++;
    }
    if (pauseIdx < pauseRanges.length && record.timestamp >= pauseRanges[pauseIdx].start && record.timestamp < pauseRanges[pauseIdx].end) {
      continue;
    }
    const pauseBefore = pauseIdx > 0 ? cumulativePause[pauseIdx - 1] : 0;
    result.push({...record, timestamp: record.timestamp - pauseBefore});
  }

  const movingTime = result.length >= 2 ? result[result.length - 1].timestamp - result[0].timestamp : 0;
  return {records: result, movingTime};
}

function stripPausesByGap(records: FitRecord[]): {records: FitRecord[]; movingTime: number} {
  const PAUSE_THRESHOLD = 20;
  const result: FitRecord[] = [records[0]];
  let adjustedTime = records[0].timestamp;
  for (let i = 1; i < records.length; i++) {
    const gap = records[i].timestamp - records[i - 1].timestamp;
    adjustedTime += gap > PAUSE_THRESHOLD ? 1 : gap;
    result.push({...records[i], timestamp: adjustedTime});
  }
  const movingTime = result[result.length - 1].timestamp - result[0].timestamp;
  return {records: result, movingTime};
}

// ── Formatters ─────────────────────────────────────────────────────────

export function formatZoneDuration(seconds: number): string {
  if (seconds >= 3600) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${h}h ${m}m`;
  }
  if (seconds >= 60) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}m ${s}s`;
  }
  return `${seconds}s`;
}

export function formatZoneDistance(meters: number): string {
  if (meters == null || meters <= 0) return '—';
  if (meters >= 1000) return (meters / 1000).toFixed(2) + ' km';
  return Math.round(meters) + ' m';
}

export function formatBlockDistance(block: BlockSummary): string {
  return formatZoneDistance(block.distanceMeters ?? 0);
}

export function formatSpeed(speedMs: number, sportType: string): string {
  if (!speedMs || speedMs <= 0) return '—';
  if (sportType === 'SWIMMING') {
    const secPer100 = 100 / speedMs;
    const m = Math.floor(secPer100 / 60);
    const s = Math.round(secPer100 % 60);
    return `${m}:${String(s).padStart(2, '0')} /100m`;
  }
  const secPerKm = 1000 / speedMs;
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, '0')} /km`;
}

export function formatLongDate(date: Date): string {
  return new Date(date).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

// ── Synthetic block-bar chart helpers ──────────────────────────────────

export function syntheticMaxPower(blocks: BlockSummary[], ftp: number | null): number {
  const observed = blocks.reduce((m, b) => Math.max(m, b.actualPower || 0, b.targetPower || 0), 0);
  const floor = ftp ? ftp * 1.2 : 200;
  return Math.max(observed, floor, 1);
}

export function syntheticTotalDuration(blocks: BlockSummary[]): number {
  return blocks.reduce((s, b) => s + (b.durationSeconds || 0), 0) || 1;
}

export function syntheticBarWidthPct(block: BlockSummary, total: number): number {
  return ((block.durationSeconds || 0) / total) * 100;
}

export function syntheticBarHeightPct(block: BlockSummary, maxPower: number): number {
  const v = block.actualPower || block.targetPower || 0;
  return Math.max(4, (v / maxPower) * 100);
}
