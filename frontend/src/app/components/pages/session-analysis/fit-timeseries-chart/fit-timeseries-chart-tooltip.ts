import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../../../../services/zone';
import {formatPaceWithUnit} from '../../../shared/format/format.utils';
import {
    findPlannedBlock,
    getCadBlockFromValue,
    getCadFromRecord,
    kmhToPace,
    speedToPlotValue,
} from './fit-timeseries-chart.utils';

export interface HoverContext {
    records: FitRecord[];
    downsampled: FitRecord[];
    effSmoothed: number[];
    sportType: string;
    zoneBlocks: ZoneBlock[];
    blockSummaries: BlockSummary[];
    showBlocks: boolean;
    primaryMax: number;
    showPrimary: boolean;
    showHR: boolean;
    showCadence: boolean;
    showEfficiency: boolean;
    hasElevation: boolean;
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

const useZoneBlocks = (ctx: HoverContext): boolean =>
    ctx.showBlocks && ctx.zoneBlocks.length > 0;
const usePlannedBlocks = (ctx: HoverContext): boolean =>
    ctx.showBlocks && !useZoneBlocks(ctx) && ctx.blockSummaries.length > 0;

export function hoverPrimaryValue(ctx: HoverContext, idx: number, t0: number): number {
    if (useZoneBlocks(ctx)) {
        const zb = ctx.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
        if (zb) {
            return isCycling(ctx.sportType)
                ? zb.avgPower
                : speedToPlotValue(zb.avgSpeed, isSwimming(ctx.sportType), ctx.primaryMax);
        }
    } else if (usePlannedBlocks(ctx)) {
        const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
        if (pb) {
            if (isCycling(ctx.sportType)) return pb.actualPower;
            const speedKmh = pb.distanceMeters && pb.durationSeconds > 0
                ? (pb.distanceMeters / pb.durationSeconds) * 3.6 : 0;
            return speedToPlotValue(speedKmh, isSwimming(ctx.sportType), ctx.primaryMax);
        }
    }
    const rec = ctx.records[idx];
    if (isCycling(ctx.sportType)) return rec.power;
    return speedToPlotValue((rec.speed || 0) * 3.6, isSwimming(ctx.sportType), ctx.primaryMax);
}

export function hoverHR(ctx: HoverContext, idx: number, t0: number): number {
    if (useZoneBlocks(ctx)) {
        const zb = ctx.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
        if (zb) return zb.avgHR;
    } else if (usePlannedBlocks(ctx)) {
        const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
        if (pb) return pb.actualHR;
    }
    return ctx.records[idx].heartRate;
}

export function hoverCadence(ctx: HoverContext, idx: number, t0: number): number {
    if (useZoneBlocks(ctx)) {
        const zb = ctx.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
        if (zb) return getCadBlockFromValue(zb.avgCadence, ctx.sportType);
    } else if (usePlannedBlocks(ctx)) {
        const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
        if (pb) return getCadBlockFromValue(pb.actualCadence, ctx.sportType);
    }
    return getCadFromRecord(ctx.records[idx], ctx.sportType);
}

export function hoverEfficiency(ctx: HoverContext, idx: number, t0: number): number {
    const effOf = (power: number, speed: number, hr: number): number => {
        if (hr <= 0) return NaN;
        const metric = isCycling(ctx.sportType) ? power : speed * 3.6;
        return metric > 0 ? metric / hr : NaN;
    };
    if (useZoneBlocks(ctx)) {
        const zb = ctx.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
        if (zb) return effOf(zb.avgPower, zb.avgSpeed, zb.avgHR);
    } else if (usePlannedBlocks(ctx)) {
        const pb = findPlannedBlock(ctx.records, ctx.blockSummaries, idx, t0);
        if (pb) {
            const speed = pb.distanceMeters && pb.durationSeconds > 0
                ? pb.distanceMeters / pb.durationSeconds : 0;
            return effOf(pb.actualPower, speed, pb.actualHR);
        }
    }
    const hoverT = ctx.records[idx].timestamp;
    const ds = ctx.downsampled;
    let nearest = 0;
    for (let i = 1; i < ds.length; i++) {
        if (Math.abs(ds[i].timestamp - hoverT) < Math.abs(ds[nearest].timestamp - hoverT)) nearest = i;
    }
    return ctx.effSmoothed[nearest];
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
    let bp: number | null = null, bpMax: number | null = null;
    let bhr: number | null = null, bcad: number | null = null;
    if (useZoneBlocks(ctx)) {
        const zb = ctx.zoneBlocks.find(b => ctx.hoverIdx >= b.startIndex && ctx.hoverIdx <= b.endIndex);
        if (zb) {
            bp = cycling ? zb.avgPower : zb.avgSpeed;
            bpMax = cycling ? zb.maxPower : zb.maxSpeed;
            bhr = zb.avgHR;
            bcad = getCadBlockFromValue(zb.avgCadence, ctx.sportType);
            blockLabel = `${zb.zoneLabel} · ${zb.zoneDescription}`;
            blockDuration = ctx.records[zb.endIndex].timestamp - ctx.records[zb.startIndex].timestamp;
        }
    } else if (usePlannedBlocks(ctx)) {
        const elapsedSec = rec.timestamp - t0;
        let acc = 0;
        for (const b of ctx.blockSummaries) {
            if (elapsedSec >= acc && elapsedSec < acc + b.durationSeconds) {
                bp = cycling ? b.actualPower
                    : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                bhr = b.actualHR;
                bcad = getCadBlockFromValue(b.actualCadence, ctx.sportType);
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
        rows.push({ label: 'Duration', value: `${dm}:${String(ds).padStart(2, '0')}`, color: 'var(--text-color)' });
    }
    if (ctx.showPrimary) {
        if (inBlock) {
            if (cycling) {
                rows.push({ label: 'Avg Power', value: `${Math.round(bp!)}W`, color: accent });
                if (bpMax) rows.push({ label: 'Max Power', value: `${Math.round(bpMax)}W`, color: accent });
            } else if (swimming) {
                const avgPace = kmhToPace(bp!);
                rows.push({ label: 'Avg Pace', value: isNaN(avgPace) ? '—' : formatPaceWithUnit(avgPace, 'SWIMMING'), color: accent });
                if (bpMax) {
                    const bestPace = kmhToPace(bpMax);
                    if (!isNaN(bestPace)) rows.push({ label: 'Best Pace', value: formatPaceWithUnit(bestPace, 'SWIMMING'), color: accent });
                }
            } else {
                rows.push({ label: 'Avg Speed', value: `${bp!.toFixed(1)} km/h`, color: accent });
                if (bpMax) rows.push({ label: 'Max Speed', value: `${bpMax.toFixed(1)} km/h`, color: accent });
            }
        } else {
            if (cycling) {
                rows.push({ label: 'Power', value: `${Math.round(rec.power)}W`, color: accent });
            } else if (swimming) {
                const pace = kmhToPace((rec.speed || 0) * 3.6);
                rows.push({ label: 'Pace', value: isNaN(pace) ? '—' : formatPaceWithUnit(pace, 'SWIMMING'), color: accent });
            } else {
                rows.push({ label: 'Speed', value: `${((rec.speed || 0) * 3.6).toFixed(1)} km/h`, color: accent });
            }
        }
    }
    if (ctx.showHR) {
        const hr = inBlock ? bhr : rec.heartRate;
        if (hr) rows.push({ label: inBlock ? 'Avg HR' : 'HR', value: `${Math.round(hr)} bpm`, color: '#e74c3c' });
    }
    if (ctx.showCadence) {
        const cad = inBlock ? bcad : getCadFromRecord(rec, ctx.sportType);
        if (cad) rows.push({ label: inBlock ? 'Avg Cad' : 'Cadence', value: `${Math.round(cad)} ${cadUnit}`, color: '#3b82f6' });
    }
    if (ctx.showEfficiency && ctx.effSmoothed.length > 0) {
        const eff = hoverEfficiency(ctx, ctx.hoverIdx, t0);
        if (!isNaN(eff)) {
            rows.push({ label: inBlock ? 'Avg Eff.' : 'Eff.', value: eff.toFixed(2), color: '#a855f7' });
        }
    }
    if (ctx.hasElevation && rec.elevation != null) {
        rows.push({ label: 'Elevation', value: `${Math.round(rec.elevation)}m`, color: '#4caf50' });
    }
    return { header, rows };
}
