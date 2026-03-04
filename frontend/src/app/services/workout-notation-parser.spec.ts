import { describe, it, expect } from 'vitest';
import { parseNotation, WorkoutNotationError } from './workout-notation-parser';

describe('WorkoutNotationParser', () => {

  // ── Primitives ────────────────────────────────────────────────────────────

  it('parses a STEADY block', () => {
    const { blocks } = parseNotation('10min85%');
    expect(blocks).toHaveLength(1);
    expect(blocks[0]).toMatchObject({ type: 'STEADY', durationSeconds: 600, intensityTarget: 85 });
  });

  it('parses a RAMP block', () => {
    const { blocks } = parseNotation('10min60>90%');
    expect(blocks).toHaveLength(1);
    expect(blocks[0]).toMatchObject({ type: 'RAMP', durationSeconds: 600, intensityStart: 60, intensityEnd: 90 });
    expect(blocks[0].intensityTarget).toBeUndefined();
  });

  it('parses a PAUSE block', () => {
    const { blocks } = parseNotation('90sP');
    expect(blocks[0]).toMatchObject({ type: 'PAUSE', durationSeconds: 90 });
  });

  it('parses WARMUP with intensity', () => {
    const { blocks } = parseNotation('15minW60%');
    expect(blocks[0]).toMatchObject({ type: 'WARMUP', durationSeconds: 900, intensityTarget: 60 });
  });

  it('parses WARMUP without intensity', () => {
    const { blocks } = parseNotation('10minW');
    expect(blocks[0].type).toBe('WARMUP');
    expect(blocks[0].intensityTarget).toBeUndefined();
  });

  it('parses COOLDOWN with intensity', () => {
    const { blocks } = parseNotation('10minC55%');
    expect(blocks[0]).toMatchObject({ type: 'COOLDOWN', durationSeconds: 600, intensityTarget: 55 });
  });

  it('parses FREE block', () => {
    const { blocks } = parseNotation('20minF');
    expect(blocks[0].type).toBe('FREE');
  });

  it('parses cadence', () => {
    const { blocks } = parseNotation('10min85%@90');
    expect(blocks[0]).toMatchObject({ intensityTarget: 85, cadenceTarget: 90 });
  });

  it('handles decimal duration', () => {
    const { blocks } = parseNotation('1.5min100%');
    expect(blocks[0].durationSeconds).toBe(90);
  });

  it('handles hours', () => {
    const { blocks } = parseNotation('1h60%');
    expect(blocks[0].durationSeconds).toBe(3600);
  });

  it('handles distance units', () => {
    const { blocks } = parseNotation('2km85%');
    expect(blocks[0].distanceMeters).toBe(2000);
    expect(blocks[0].durationSeconds).toBeUndefined();
  });

  it('no modifier defaults to FREE', () => {
    const { blocks } = parseNotation('10min');
    expect(blocks[0].type).toBe('FREE');
  });

  // ── Sequences ─────────────────────────────────────────────────────────────

  it('parses multiple segments', () => {
    const { blocks } = parseNotation('10min85%-5minP');
    expect(blocks).toHaveLength(2);
    expect(blocks[0].type).toBe('STEADY');
    expect(blocks[1].type).toBe('PAUSE');
  });

  it('parses full workout', () => {
    // 1 + 5×2 + 1 = 12 blocks
    const { blocks } = parseNotation('10minW60%-5*(3min105%-2minP)-10minC55%');
    expect(blocks).toHaveLength(12);
    expect(blocks[0].type).toBe('WARMUP');
    expect(blocks[1].type).toBe('INTERVAL');
    expect(blocks[2].type).toBe('PAUSE');
    expect(blocks[11].type).toBe('COOLDOWN');
  });

  // ── Interval expansion ────────────────────────────────────────────────────

  it('expands intervals', () => {
    const { blocks } = parseNotation('10*(1min100%-1minP)');
    expect(blocks).toHaveLength(20);
    for (let i = 0; i < 20; i += 2) {
      expect(blocks[i].type).toBe('INTERVAL');
      expect(blocks[i + 1].type).toBe('PAUSE');
    }
  });

  it('N% inside interval becomes INTERVAL, not STEADY', () => {
    const { blocks } = parseNotation('3*(2min90%)');
    blocks.forEach(b => expect(b.type).toBe('INTERVAL'));
  });

  it('expands nested intervals', () => {
    // 2*(3*(30s120%-30sP)-1minP) → 2 × (3×2 + 1) = 14 blocks
    const { blocks } = parseNotation('2*(3*(30s120%-30sP)-1minP)');
    expect(blocks).toHaveLength(14);
  });

  it('RAMP inside interval stays RAMP', () => {
    const { blocks } = parseNotation('2*(5min60>90%-2minP)');
    expect(blocks[0].type).toBe('RAMP');
    expect(blocks[1].type).toBe('PAUSE');
  });

  // ── Duration totals ────────────────────────────────────────────────────────

  it('computes correct totalDurationSeconds', () => {
    const { totalDurationSeconds } = parseNotation('10*(1min100%-1minP)');
    expect(totalDurationSeconds).toBe(1200); // 10 × 2min = 20min
  });

  // ── Estimated metrics ─────────────────────────────────────────────────────

  it('returns estimatedIf and estimatedTss', () => {
    const result = parseNotation('60min75%');
    expect(result.estimatedIf).toBe(0.75);
    expect(result.estimatedTss).toBeGreaterThan(0);
  });

  // ── Error cases ───────────────────────────────────────────────────────────

  it('throws on empty notation', () => {
    expect(() => parseNotation('')).toThrow(WorkoutNotationError);
  });

  it('throws on unknown modifier', () => {
    expect(() => parseNotation('10minX')).toThrow(WorkoutNotationError);
  });

  it('throws on missing unit', () => {
    expect(() => parseNotation('10*5min')).toThrow(WorkoutNotationError);
  });
});
