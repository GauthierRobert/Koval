import { describe, expect, it } from 'vitest';
import { computeDriftCurves } from './fit-timeseries-chart.utils';
import { FitRecord } from '../../../../services/metrics.service';

function makeRide(
  buildSample: (i: number, elapsedSec: number) => Partial<FitRecord>,
  durationSec = 1800,
): FitRecord[] {
  const out: FitRecord[] = [];
  for (let i = 0; i < durationSec; i++) {
    const base: FitRecord = {
      timestamp: i,
      power: 0,
      heartRate: 0,
      cadence: 0,
      speed: 0,
      distance: i,
    };
    out.push({ ...base, ...buildSample(i, i) });
  }
  return out;
}

describe('computeDriftCurves', () => {
  it('returns null for swimming', () => {
    const records = makeRide(() => ({ power: 200, heartRate: 140 }));
    expect(computeDriftCurves(records, 'SWIMMING')).toBeNull();
  });

  it('returns null when cycling has no power data', () => {
    const records = makeRide(() => ({ heartRate: 140, speed: 8 }));
    expect(computeDriftCurves(records, 'CYCLING')).toBeNull();
  });

  it('returns null when there is no HR data', () => {
    const records = makeRide(() => ({ power: 200 }));
    expect(computeDriftCurves(records, 'CYCLING')).toBeNull();
  });

  it('returns null for sessions shorter than the minimum sample threshold', () => {
    const records = makeRide(() => ({ power: 200, heartRate: 140 }), 120);
    expect(computeDriftCurves(records, 'CYCLING')).toBeNull();
  });

  it('returns null when nothing meaningful remains after the baseline window', () => {
    // 7 min ride: skip ~42s, baseline ≥ 5 min ends at ~5:42, leaving ~78s after,
    // which is below the DRIFT_POST_MIN_SEC = 120s gate.
    const records = makeRide(() => ({ power: 200, heartRate: 140 }), 7 * 60);
    expect(computeDriftCurves(records, 'CYCLING')).toBeNull();
  });

  it('baselines cycling over the warmup-trimmed stable window', () => {
    const records = makeRide(() => ({ power: 200, heartRate: 140 }));
    const curves = computeDriftCurves(records, 'CYCLING');
    expect(curves).not.toBeNull();
    expect(curves!.usePower).toBe(true);
    expect(curves!.baselineHR).toBeCloseTo(140, 5);
    expect(curves!.baselineOut).toBeCloseTo(200, 5);
    // 30-min ride → skip = 3 min (capped), baseline = 5 min (floor) → covers 3–8 min.
    expect(curves!.baselineStartSec).toBeCloseTo(180, 0);
    expect(curves!.baselineEndSec).toBeCloseTo(180 + 300, 0);
    // A steady ride sits at exactly 100% on both curves throughout.
    const mid = curves!.hrNormPct[900];
    expect(mid).toBeCloseTo(100, 5);
    expect(curves!.outNormPct[900]).toBeCloseTo(100, 5);
  });

  it('ignores a cold-HR warmup ramp instead of baselining off it', () => {
    // HR ramps from 100 → 150 over the first 3 min, then holds at 150. The new
    // window (3–8 min) should land entirely on the 150 plateau, so steady-state
    // samples read as 100%. Under the old "first 5 min" baseline this would
    // falsely report ~+10% drift across the rest of the session.
    const records = makeRide((_i, t) => {
      const hr = t < 180 ? 100 + (t / 180) * 50 : 150;
      return { power: 200, heartRate: hr };
    });
    const curves = computeDriftCurves(records, 'CYCLING')!;
    expect(curves.baselineHR).toBeCloseTo(150, 1);
    const late = curves.hrNormPct[curves.hrNormPct.length - 60];
    expect(late).toBeCloseTo(100, 1);
  });

  it('expands the baseline window proportionally on long sessions (capped at 15 min)', () => {
    // 3-hour ride: 10% = 18 min → clamped to 15 min.
    const records = makeRide(() => ({ power: 200, heartRate: 140 }), 3 * 3600);
    const curves = computeDriftCurves(records, 'CYCLING')!;
    expect(curves.baselineStartSec).toBeCloseTo(180, 0);
    expect(curves.baselineEndSec - curves.baselineStartSec).toBeCloseTo(900, 0);
  });

  it('detects positive cardiac drift when HR rises while power holds', () => {
    // Steady 200W; HR creeps from 140 (start) to 154 (end of session): +10%.
    const records = makeRide((_i, t) => ({
      power: 200,
      heartRate: 140 + (t / 1800) * 14,
    }));
    const curves = computeDriftCurves(records, 'CYCLING')!;
    const endHR = curves.hrNormPct[curves.hrNormPct.length - 60];
    const endOut = curves.outNormPct[curves.outNormPct.length - 60];
    expect(endHR).toBeGreaterThan(105);
    expect(endHR).toBeLessThan(115);
    expect(endOut).toBeCloseTo(100, 1);
  });

  it('uses speed as the output curve for running', () => {
    const records = makeRide(() => ({ speed: 3.5, heartRate: 150 }));
    const curves = computeDriftCurves(records, 'RUNNING')!;
    expect(curves.usePower).toBe(false);
    expect(curves.baselineOut).toBeCloseTo(3.5, 5);
    expect(curves.outNormPct[900]).toBeCloseTo(100, 5);
  });

  it('detects pace decay (slowing) on a run with stable HR', () => {
    // HR holds at 150; speed drops from 3.5 m/s to ~3.0 m/s by the end.
    const records = makeRide((_i, t) => ({
      heartRate: 150,
      speed: 3.5 - (t / 1800) * 0.5,
    }));
    const curves = computeDriftCurves(records, 'RUNNING')!;
    const endOut = curves.outNormPct[curves.outNormPct.length - 60];
    const endHR = curves.hrNormPct[curves.hrNormPct.length - 60];
    expect(endOut).toBeLessThan(95);
    expect(endHR).toBeCloseTo(100, 1);
  });

  it('skips invalid samples (HR=0 or output=0) without poisoning the curve', () => {
    // First 5 min are valid, then half the samples have HR=0; the curve over the
    // valid samples should still be ~100%.
    const records = makeRide((_i, t) => {
      if (t < 300) return { power: 200, heartRate: 140 };
      return { power: 200, heartRate: t % 2 === 0 ? 140 : 0 };
    });
    const curves = computeDriftCurves(records, 'CYCLING')!;
    const lateHR = curves.hrNormPct[1500];
    expect(lateHR).toBeCloseTo(100, 1);
  });
});
