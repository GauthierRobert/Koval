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

/** Per-sample, normalized HR and output curves (both as % of their session baseline).
 *  Baseline = average over a "first stable window" after a warmup skip.
 *  A sample is "valid" when HR > 0 AND (cycling: power > 0; running: speed > 0).
 *  The two curves are aligned 1:1 with `records`; gaps (invalid samples) hold NaN.
 *  `baselineStartSec` / `baselineEndSec` are elapsed seconds from session start,
 *  exposed so the UI can show the user what window was used. */
export interface DriftCurves {
    hrNormPct: number[];
    outNormPct: number[];
    baselineHR: number;
    baselineOut: number;
    baselineStartSec: number;
    baselineEndSec: number;
    usePower: boolean;
}

const DRIFT_SKIP_FRAC = 0.10;       // discard the first 10% of the session as warmup
const DRIFT_SKIP_CAP_SEC = 180;     // …but never skip more than 3 min
const DRIFT_BASE_FRAC = 0.10;       // baseline window = 10% of session
const DRIFT_BASE_MIN_SEC = 300;     // …clamped to [5, 15] min
const DRIFT_BASE_MAX_SEC = 900;
const DRIFT_POST_MIN_SEC = 120;     // need ≥2 min of usable data after the baseline
const DRIFT_SMOOTH_SEC = 60;        // ±30s rolling smoother around each sample

/**
 * Compute HR-norm and Output-norm time series for cardiac-drift visualization.
 * Returns null when the sport doesn't support drift (swimming) or when there
 * isn't enough usable data (no HR, no power on a cycling ride, baseline window
 * empty, or no meaningful post-baseline period to compare against).
 *
 * The baseline skips the first ~3 min (warmup) and then averages a 5–15 min
 * stable window (10% of session length, clamped). This avoids the "cold HR"
 * bias where a baseline taken from the first minutes of a ride sits below
 * steady-state and makes every later sample look like drift.
 *
 * Both curves are smoothed with a centered DRIFT_SMOOTH_SEC window so the lines
 * read as drift trends, not heartbeat noise. The smoother and the baseline use
 * the same "valid sample" definition so the two are directly comparable.
 */
export function computeDriftCurves(records: FitRecord[], sportType: string): DriftCurves | null {
    if (sportType === 'SWIMMING') return null;
    if (records.length < 2) return null;

    const hasPower = sportType === 'CYCLING' && records.some(r => r.power > 0);
    if (sportType === 'CYCLING' && !hasPower) return null;
    const usePower = hasPower;
    const outOf = (r: FitRecord) => (usePower ? r.power : r.speed);
    const isValid = (r: FitRecord) => r.heartRate > 0 && outOf(r) > 0;

    const t0 = records[0].timestamp;
    const totalSec = records[records.length - 1].timestamp - t0;

    // Pick the baseline window: warmup-skip + adaptive width.
    const skipSec = Math.min(DRIFT_SKIP_CAP_SEC, totalSec * DRIFT_SKIP_FRAC);
    const baseSec = Math.min(
        DRIFT_BASE_MAX_SEC,
        Math.max(DRIFT_BASE_MIN_SEC, totalSec * DRIFT_BASE_FRAC),
    );
    const baselineStartSec = skipSec;
    const baselineEndSec = skipSec + baseSec;
    // Need at least DRIFT_POST_MIN_SEC of session left after the baseline to
    // have anything meaningful to compare against.
    if (totalSec < baselineEndSec + DRIFT_POST_MIN_SEC) return null;

    // Baseline = avg over the chosen window of valid samples only.
    let baseHRSum = 0, baseOutSum = 0, baseN = 0;
    for (const r of records) {
        const elapsed = r.timestamp - t0;
        if (elapsed < baselineStartSec) continue;
        if (elapsed > baselineEndSec) break;
        if (!isValid(r)) continue;
        baseHRSum += r.heartRate;
        baseOutSum += outOf(r);
        baseN++;
    }
    // Need at least ~30s worth of valid baseline samples (assumes ~1Hz cadence).
    if (baseN < 30) return null;
    const baselineHR = baseHRSum / baseN;
    const baselineOut = baseOutSum / baseN;
    if (baselineHR <= 0 || baselineOut <= 0) return null;

    // Centered rolling average with a two-pointer window (timestamp-indexed,
    // not sample-indexed, since FIT records can have variable cadence).
    const n = records.length;
    const hrNormPct = new Array<number>(n);
    const outNormPct = new Array<number>(n);
    const half = DRIFT_SMOOTH_SEC / 2;
    let lo = 0, hi = 0;
    let winHR = 0, winOut = 0, winN = 0;
    for (let i = 0; i < n; i++) {
        const tCenter = records[i].timestamp;
        // Expand right edge.
        while (hi < n && records[hi].timestamp <= tCenter + half) {
            if (isValid(records[hi])) {
                winHR += records[hi].heartRate;
                winOut += outOf(records[hi]);
                winN++;
            }
            hi++;
        }
        // Shrink left edge.
        while (lo < hi && records[lo].timestamp < tCenter - half) {
            if (isValid(records[lo])) {
                winHR -= records[lo].heartRate;
                winOut -= outOf(records[lo]);
                winN--;
            }
            lo++;
        }
        if (winN > 0) {
            hrNormPct[i] = (winHR / winN / baselineHR) * 100;
            outNormPct[i] = (winOut / winN / baselineOut) * 100;
        } else {
            hrNormPct[i] = NaN;
            outNormPct[i] = NaN;
        }
    }

    return {
        hrNormPct,
        outNormPct,
        baselineHR,
        baselineOut,
        baselineStartSec,
        baselineEndSec,
        usePower,
    };
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
