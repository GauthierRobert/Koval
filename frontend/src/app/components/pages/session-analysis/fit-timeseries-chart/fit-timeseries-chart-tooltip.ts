import { FitRecord } from '../../../../services/metrics.service';
import { BlockSummary } from '../../../../services/workout-execution.service';
import { ZoneBlock } from '../../../../services/zone';
import { formatPaceWithUnit } from '../../../shared/format/format.utils';
import {
  DriftCurves,
  findPlannedBlock,
  getCadBlockFromValue,
  kmhToPace,
  lerpDsValue,
  speedToPlotValue,
} from './fit-timeseries-chart.utils';

export interface HoverContext {
  records: FitRecord[];
  downsampled: FitRecord[];
  sportType: string;
  zoneBlocks: ZoneBlock[];
  blockSummaries: BlockSummary[];
  showBlocks: boolean;
  primaryMax: number;
  showPrimary: boolean;
  showHR: boolean;
  showCadence: boolean;
  hasElevation: boolean;
  showDrift: boolean;
  driftCurves: DriftCurves | null;
  accentHex: string;
  hoverIdx: number;
}

export interface TooltipRow {
  label: string;
  value: string;
  color: string;
}

export interface TooltipContent {
  header: string;
  rows: TooltipRow[];
}

const isCycling = (sportType: string) => sportType === 'CYCLING';
const isSwimming = (sportType: string) => sportType === 'SWIMMING';

const useZoneBlocks = (ctx: HoverContext): boolean => ctx.showBlocks && ctx.zoneBlocks.length > 0;
const usePlannedBlocks = (ctx: HoverContext): boolean =>
  ctx.showBlocks && !useZoneBlocks(ctx) && ctx.blockSummaries.length > 0;

export function hoverPrimaryValue(ctx: HoverContext, idx: number, t0: number): number {
  if (useZoneBlocks(ctx)) {
    const zb = ctx.zoneBlocks.find((b) => idx >= b.startIndex && idx <= b.endIndex);
    if (zb) {
      return isCycling(ctx.sportType)
        ? zb.avgPower
        : speedToPlotValue(zb.avgSpeed, isSwimming(ctx.sportType), ctx.primaryMax);
    }
  } else if (usePlannedBlocks(ctx)) {
    const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
    if (pb) {
      if (isCycling(ctx.sportType)) return pb.actualPower;
      const speedKmh =
        pb.distanceMeters && pb.durationSeconds > 0
          ? (pb.distanceMeters / pb.durationSeconds) * 3.6
          : 0;
      return speedToPlotValue(speedKmh, isSwimming(ctx.sportType), ctx.primaryMax);
    }
  }
  // No-block path: the visible line is drawn from the 30s downsampled series.
  // Snap to the lerped bucket value so the dot rides the rendered line, not the raw spike.
  const t = ctx.records[idx].timestamp;
  if (isCycling(ctx.sportType)) {
    return lerpDsValue(ctx.downsampled, t, (r) => r.power);
  }
  const kmh = lerpDsValue(ctx.downsampled, t, (r) => (r.speed || 0) * 3.6);
  return speedToPlotValue(kmh, isSwimming(ctx.sportType), ctx.primaryMax);
}

/** Speed (km/h) for the dedicated speed panel's hover dot. In block mode this
 * snaps to the block's average speed so the dot rides the stepped line rather
 * than the raw per-record spike. */
export function hoverSpeed(ctx: HoverContext, idx: number, t0: number): number {
  if (useZoneBlocks(ctx)) {
    const zb = ctx.zoneBlocks.find((b) => idx >= b.startIndex && idx <= b.endIndex);
    if (zb) return zb.avgSpeed;
  } else if (usePlannedBlocks(ctx)) {
    const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
    if (pb) {
      return pb.distanceMeters && pb.durationSeconds > 0
        ? (pb.distanceMeters / pb.durationSeconds) * 3.6
        : 0;
    }
  }
  const t = ctx.records[idx].timestamp;
  return lerpDsValue(ctx.downsampled, t, (r) => (r.speed || 0) * 3.6);
}

export function hoverHR(ctx: HoverContext, idx: number, t0: number): number {
  if (useZoneBlocks(ctx)) {
    const zb = ctx.zoneBlocks.find((b) => idx >= b.startIndex && idx <= b.endIndex);
    if (zb) return zb.avgHR;
  } else if (usePlannedBlocks(ctx)) {
    const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
    if (pb) return pb.actualHR;
  }
  const t = ctx.records[idx].timestamp;
  return lerpDsValue(ctx.downsampled, t, (r) => r.heartRate);
}

export function hoverCadence(ctx: HoverContext, idx: number, t0: number): number {
  if (useZoneBlocks(ctx)) {
    const zb = ctx.zoneBlocks.find((b) => idx >= b.startIndex && idx <= b.endIndex);
    if (zb) return getCadBlockFromValue(zb.avgCadence, ctx.sportType);
  } else if (usePlannedBlocks(ctx)) {
    const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
    if (pb) return getCadBlockFromValue(pb.actualCadence, ctx.sportType);
  }
  const t = ctx.records[idx].timestamp;
  const cadAvg = lerpDsValue(ctx.downsampled, t, (r) => r.cadence);
  return getCadBlockFromValue(cadAvg, ctx.sportType);
}

export function buildTooltipContent(ctx: HoverContext): TooltipContent {
  const rec = ctx.records[ctx.hoverIdx];
  const t0 = ctx.records[0].timestamp;
  const accent = ctx.accentHex;
  const cycling = isCycling(ctx.sportType);
  const swimming = isSwimming(ctx.sportType);
  const cadUnit = ctx.sportType === 'RUNNING' ? 'spm' : 'rpm';

  let blockLabel: string | null = null;
  let blockDuration: number | null = null;
  let bp: number | null = null,
    bpMax: number | null = null;
  let bhr: number | null = null,
    bcad: number | null = null;
  let bSpeedKmh: number | null = null;
  if (useZoneBlocks(ctx)) {
    const zb = ctx.zoneBlocks.find(
      (b) => ctx.hoverIdx >= b.startIndex && ctx.hoverIdx <= b.endIndex,
    );
    if (zb) {
      bp = cycling ? zb.avgPower : zb.avgSpeed;
      bpMax = cycling ? zb.maxPower : zb.maxSpeed;
      bhr = zb.avgHR;
      bcad = getCadBlockFromValue(zb.avgCadence, ctx.sportType);
      if (cycling) bSpeedKmh = zb.avgSpeed;
      blockLabel = `${zb.zoneLabel} · ${zb.zoneDescription}`;
      blockDuration = ctx.records[zb.endIndex].timestamp - ctx.records[zb.startIndex].timestamp;
    }
  } else if (usePlannedBlocks(ctx)) {
    const elapsedSec = rec.timestamp - t0;
    let acc = 0;
    for (const b of ctx.blockSummaries) {
      if (elapsedSec >= acc && elapsedSec < acc + b.durationSeconds) {
        bp = cycling
          ? b.actualPower
          : b.distanceMeters && b.durationSeconds > 0
            ? (b.distanceMeters / b.durationSeconds) * 3.6
            : 0;
        bhr = b.actualHR;
        bcad = getCadBlockFromValue(b.actualCadence, ctx.sportType);
        if (cycling && b.distanceMeters && b.durationSeconds > 0) {
          bSpeedKmh = (b.distanceMeters / b.durationSeconds) * 3.6;
        }
        blockLabel = b.label;
        blockDuration = b.durationSeconds;
        break;
      }
      acc += b.durationSeconds;
    }
  }
  const inBlock = (useZoneBlocks(ctx) || usePlannedBlocks(ctx)) && bp !== null;

  const elapsed = rec.timestamp - t0;
  const em = Math.floor(elapsed / 60);
  const es = elapsed % 60;
  const elapsedStr = `${em}:${String(es).padStart(2, '0')}`;
  const header = blockLabel ?? elapsedStr;

  const rows: TooltipRow[] = [];
  if (blockLabel) {
    rows.push({ label: 'Time', value: elapsedStr, color: 'var(--text-color)' });
  }
  if (inBlock && blockDuration != null) {
    const dm = Math.floor(blockDuration / 60);
    const ds = Math.round(blockDuration % 60);
    rows.push({
      label: 'Duration',
      value: `${dm}:${String(ds).padStart(2, '0')}`,
      color: 'var(--text-color)',
    });
  }
  if (ctx.showPrimary) {
    if (inBlock) {
      if (cycling) {
        rows.push({ label: 'Avg Power', value: `${Math.round(bp!)}W`, color: accent });
        if (bpMax) rows.push({ label: 'Max Power', value: `${Math.round(bpMax)}W`, color: accent });
        if (bSpeedKmh != null && bSpeedKmh > 0) {
          rows.push({
            label: 'Avg Speed',
            value: `${bSpeedKmh.toFixed(1)} km/h`,
            color: '#22d3ee',
          });
        }
      } else if (swimming) {
        const avgPace = kmhToPace(bp!);
        rows.push({
          label: 'Avg Pace',
          value: isNaN(avgPace) ? '—' : formatPaceWithUnit(avgPace, 'SWIMMING'),
          color: accent,
        });
        if (bpMax) {
          const bestPace = kmhToPace(bpMax);
          if (!isNaN(bestPace))
            rows.push({
              label: 'Best Pace',
              value: formatPaceWithUnit(bestPace, 'SWIMMING'),
              color: accent,
            });
        }
      } else {
        const avgPace = bp! > 0.5 ? 3600 / bp! : NaN;
        rows.push({
          label: 'Avg Pace',
          value: isNaN(avgPace) ? '—' : formatPaceWithUnit(avgPace, ctx.sportType),
          color: accent,
        });
        if (bpMax) {
          const bestPace = bpMax > 0.5 ? 3600 / bpMax : NaN;
          if (!isNaN(bestPace))
            rows.push({
              label: 'Best Pace',
              value: formatPaceWithUnit(bestPace, ctx.sportType),
              color: accent,
            });
        }
      }
    } else {
      // Raw-line mode: show the smoothed (30s bucket) value so it matches what the chart shows.
      const tRec = rec.timestamp;
      if (cycling) {
        const p = lerpDsValue(ctx.downsampled, tRec, (r) => r.power);
        rows.push({ label: 'Power', value: `${Math.round(p)}W`, color: accent });
        const kmh = lerpDsValue(ctx.downsampled, tRec, (r) => (r.speed || 0) * 3.6);
        if (kmh > 0) {
          rows.push({ label: 'Speed', value: `${kmh.toFixed(1)} km/h`, color: '#22d3ee' });
        }
      } else if (swimming) {
        const kmh = lerpDsValue(ctx.downsampled, tRec, (r) => (r.speed || 0) * 3.6);
        const pace = kmhToPace(kmh);
        rows.push({
          label: 'Pace',
          value: isNaN(pace) ? '—' : formatPaceWithUnit(pace, 'SWIMMING'),
          color: accent,
        });
      } else {
        const kmh = lerpDsValue(ctx.downsampled, tRec, (r) => (r.speed || 0) * 3.6);
        const pace = kmh > 0.5 ? 3600 / kmh : NaN;
        rows.push({
          label: 'Pace',
          value: isNaN(pace) ? '—' : formatPaceWithUnit(pace, ctx.sportType),
          color: accent,
        });
      }
    }
  }
  if (ctx.showHR) {
    const hr = inBlock ? bhr : lerpDsValue(ctx.downsampled, rec.timestamp, (r) => r.heartRate);
    if (hr)
      rows.push({
        label: inBlock ? 'Avg HR' : 'HR',
        value: `${Math.round(hr)} bpm`,
        color: '#e74c3c',
      });
  }
  if (ctx.showCadence) {
    const cad = inBlock
      ? bcad
      : getCadBlockFromValue(
          lerpDsValue(ctx.downsampled, rec.timestamp, (r) => r.cadence),
          ctx.sportType,
        );
    if (cad)
      rows.push({
        label: inBlock ? 'Avg Cad' : 'Cadence',
        value: `${Math.round(cad)} ${cadUnit}`,
        color: '#3b82f6',
      });
  }
  if (ctx.hasElevation && rec.elevation != null) {
    rows.push({ label: 'Elevation', value: `${Math.round(rec.elevation)}m`, color: '#4caf50' });
  }
  if (ctx.showDrift && ctx.driftCurves) {
    const hrN = ctx.driftCurves.hrNormPct[ctx.hoverIdx];
    const outN = ctx.driftCurves.outNormPct[ctx.hoverIdx];
    if (Number.isFinite(hrN)) {
      rows.push({ label: 'HR vs base', value: `${hrN.toFixed(1)}%`, color: '#e74c3c' });
    }
    if (Number.isFinite(outN)) {
      const outLabel = ctx.driftCurves.usePower ? 'Pwr vs base' : 'Spd vs base';
      rows.push({ label: outLabel, value: `${outN.toFixed(1)}%`, color: accent });
    }
    if (Number.isFinite(hrN) && Number.isFinite(outN)) {
      const drift = hrN - outN;
      const sign = drift >= 0 ? '+' : '';
      rows.push({
        label: 'Drift',
        value: `${sign}${drift.toFixed(1)}pp`,
        color: drift > 5 ? '#e74c3c' : drift > 2 ? 'oklch(0.75 0.16 75)' : 'var(--success-color)',
      });
    }
  }
  return { header, rows };
}
