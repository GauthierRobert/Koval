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

/**
 * Linearly interpolate a numeric field of the downsampled series at timestamp t.
 * Used so the hover dot / tooltip value rides the visible (smoothed) line in raw mode,
 * instead of the spike-y raw record under the cursor.
 */
export function lerpDsValue(
    ds: FitRecord[],
    t: number,
    accessor: (r: FitRecord) => number,
): number {
    if (ds.length === 0) return NaN;
    if (ds.length === 1) return accessor(ds[0]);
    if (t <= ds[0].timestamp) return accessor(ds[0]);
    if (t >= ds[ds.length - 1].timestamp) return accessor(ds[ds.length - 1]);
    let lo = 0,
        hi = ds.length - 1;
    while (hi - lo > 1) {
        const mid = (lo + hi) >> 1;
        if (ds[mid].timestamp <= t) lo = mid;
        else hi = mid;
    }
    const a = ds[lo],
        b = ds[hi];
    const span = b.timestamp - a.timestamp;
    if (span <= 0) return accessor(a);
    const f = (t - a.timestamp) / span;
    return accessor(a) + (accessor(b) - accessor(a)) * f;
}

export interface SelectionStats {
    startSec: number;
    endSec: number;
    durationSec: number;
    distanceMeters: number;
    avgPower: number;
    maxPower: number;
    normalizedPower: number;
    avgHR: number;
    maxHR: number;
    avgCadence: number;
    avgSpeedKmh: number;
    maxSpeedKmh: number;
    elevationGain: number;
    sampleCount: number;
}

/**
 * Compute summary metrics for a contiguous slice of FIT records.
 * Range is inclusive on both ends; indices are clamped to the records array.
 * Normalized power uses the standard 30s rolling-avg-of-4th-power formula.
 */
export function computeSelectionStats(
    records: FitRecord[],
    startIdx: number,
    endIdx: number,
): SelectionStats | null {
    if (records.length === 0) return null;
    const lo = Math.max(0, Math.min(startIdx, endIdx));
    const hi = Math.min(records.length - 1, Math.max(startIdx, endIdx));
    if (hi <= lo) return null;
    const t0 = records[0].timestamp;
    const startSec = records[lo].timestamp - t0;
    const endSec = records[hi].timestamp - t0;
    const durationSec = endSec - startSec;
    const distanceMeters = (records[hi].distance ?? 0) - (records[lo].distance ?? 0);

    let powerSum = 0,
        powerSamples = 0,
        maxPower = 0;
    let hrSum = 0,
        hrSamples = 0,
        maxHR = 0;
    let cadSum = 0,
        cadSamples = 0;
    let speedSum = 0,
        speedSamples = 0,
        maxSpeed = 0;
    let elevGain = 0;
    let prevElev: number | null = null;

    for (let i = lo; i <= hi; i++) {
        const r = records[i];
        if (r.power > 0) {
            powerSum += r.power;
            powerSamples++;
            if (r.power > maxPower) maxPower = r.power;
        }
        if (r.heartRate > 0) {
            hrSum += r.heartRate;
            hrSamples++;
            if (r.heartRate > maxHR) maxHR = r.heartRate;
        }
        if (r.cadence > 0) {
            cadSum += r.cadence;
            cadSamples++;
        }
        if (r.speed > 0) {
            const s = r.speed;
            speedSum += s;
            speedSamples++;
            if (s > maxSpeed) maxSpeed = s;
        }
        if (r.elevation != null) {
            if (prevElev != null) {
                const d = r.elevation - prevElev;
                if (d > 0) elevGain += d;
            }
            prevElev = r.elevation;
        }
    }

    // Normalized power: rolling 30s avg → ^4 → mean → ^0.25
    let normalizedPower = 0;
    if (powerSamples > 0 && hi - lo >= 30) {
        const w: number[] = [];
        let sum = 0;
        for (let i = lo; i <= hi; i++) {
            sum += records[i].power;
            w.push(records[i].power);
            if (w.length > 30) sum -= w.shift()!;
            if (w.length === 30) {
                const avg = sum / 30;
                normalizedPower += Math.pow(avg, 4);
            }
        }
        const n = hi - lo - 30 + 1;
        if (n > 0) normalizedPower = Math.pow(normalizedPower / n, 0.25);
    }

    return {
        startSec,
        endSec,
        durationSec,
        distanceMeters,
        avgPower: powerSamples ? powerSum / powerSamples : 0,
        maxPower,
        normalizedPower,
        avgHR: hrSamples ? hrSum / hrSamples : 0,
        maxHR,
        avgCadence: cadSamples ? cadSum / cadSamples : 0,
        avgSpeedKmh: speedSamples ? (speedSum / speedSamples) * 3.6 : 0,
        maxSpeedKmh: maxSpeed * 3.6,
        elevationGain: elevGain,
        sampleCount: hi - lo + 1,
    };
}
