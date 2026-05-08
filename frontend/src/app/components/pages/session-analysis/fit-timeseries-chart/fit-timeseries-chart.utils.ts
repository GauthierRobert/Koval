import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';

export interface ThemeColors {
    accentRgb: [number, number, number];
    accentHex: string;
    textAlpha40: string;
    textAlpha30: string;
    gridAlpha15: string;
    gridAlpha12: string;
    crosshairAlpha: string;
    dotStroke: string;
}

export function marginsForWidth(W: number): { mL: number; mR: number } {
    return W < 500 ? { mL: 28, mR: 16 } : { mL: 48, mR: 48 };
}

export function kmhToPace(speedKmh: number): number {
    return speedKmh > 0.5 ? 360 / speedKmh : NaN;
}

export function speedToPlotValue(
    speedKmh: number,
    isSwimming: boolean,
    primaryMax: number,
): number {
    if (!isSwimming) return speedKmh;
    const p = kmhToPace(speedKmh);
    return isNaN(p) ? primaryMax : p;
}

export function getCadFromRecord(r: FitRecord, sportType: string): number {
    return sportType === 'RUNNING' ? r.cadence * 2 : r.cadence;
}

export function getCadBlockFromValue(c: number, sportType: string): number {
    return sportType === 'RUNNING' ? c * 2 : c;
}

export function cssToRgb(css: string): [number, number, number] {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = css;
    const out = ctx.fillStyle;
    if (out.startsWith('#')) {
        return [
            parseInt(out.slice(1, 3), 16),
            parseInt(out.slice(3, 5), 16),
            parseInt(out.slice(5, 7), 16),
        ];
    }
    const m = out.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
    return m ? [+m[1], +m[2], +m[3]] : [255, 157, 0];
}

export function accentAlphaFromRgb(rgb: [number, number, number], a: number): string {
    return `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${a})`;
}

export function resolveThemeColors(): ThemeColors {
    const s = getComputedStyle(document.documentElement);
    const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
    const raw = s.getPropertyValue('--accent-color').trim();
    const accentRgb: [number, number, number] = raw ? cssToRgb(raw) : [255, 157, 0];
    const accentHex = `rgb(${accentRgb.join(',')})`;
    if (isDark) {
        return {
            accentRgb, accentHex,
            textAlpha40: 'rgba(255,255,255,0.4)',
            textAlpha30: 'rgba(255,255,255,0.3)',
            gridAlpha15: 'rgba(255,255,255,0.15)',
            gridAlpha12: 'rgba(255,255,255,0.12)',
            crosshairAlpha: 'rgba(255,255,255,0.25)',
            dotStroke: 'rgba(255,255,255,0.8)',
        };
    }
    return {
        accentRgb, accentHex,
        textAlpha40: 'rgba(0,0,0,0.45)',
        textAlpha30: 'rgba(0,0,0,0.35)',
        gridAlpha15: 'rgba(0,0,0,0.12)',
        gridAlpha12: 'rgba(0,0,0,0.08)',
        crosshairAlpha: 'rgba(0,0,0,0.2)',
        dotStroke: 'rgba(0,0,0,0.6)',
    };
}

export function pickTickInterval(totalSec: number): number {
    const targets = [60, 300, 600, 900, 1800, 3600];
    const desired = totalSec / 8;
    return targets.reduce((a, b) => Math.abs(a - desired) < Math.abs(b - desired) ? a : b);
}

export function downsample(records: FitRecord[], bucketSec: number): FitRecord[] {
    if (records.length < 2) return [...records];
    const result: FitRecord[] = [];
    let bStart = 0;
    for (let i = 1; i <= records.length; i++) {
        if (i < records.length && records[i].timestamp - records[bStart].timestamp < bucketSec) continue;
        const slice = records.slice(bStart, i);
        const n = slice.length;
        let power = 0, hr = 0, cad = 0, speed = 0, elev = 0, elevCount = 0;
        for (const r of slice) {
            power += r.power;
            hr += r.heartRate;
            cad += r.cadence;
            speed += r.speed;
            if (r.elevation != null) { elev += r.elevation; elevCount++; }
        }
        result.push({
            timestamp: slice[Math.floor(n / 2)].timestamp,
            power: power / n,
            heartRate: hr / n,
            cadence: cad / n,
            speed: speed / n,
            distance: slice[n - 1].distance,
            elevation: elevCount > 0 ? elev / elevCount : (undefined as unknown as number),
        });
        bStart = i;
    }
    return result;
}

export function findPlannedBlock(
    records: FitRecord[],
    blockSummaries: BlockSummary[],
    idx: number,
    t0: number,
): BlockSummary | null {
    const elapsed = records[idx].timestamp - t0;
    let acc = 0;
    for (const b of blockSummaries) {
        if (elapsed >= acc && elapsed < acc + b.durationSeconds) return b;
        acc += b.durationSeconds;
    }
    return null;
}
