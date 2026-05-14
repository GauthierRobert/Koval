/**
 * Workout Notation Parser — standalone, zero-dependency TypeScript module.
 *
 * Converts compact workout notation into structured WorkoutBlock objects.
 *
 * Grammar:
 *   plan     ::= segment ( '-' segment )*
 *   segment  ::= interval | block
 *   interval ::= INTEGER '*(' plan ')'
 *   block    ::= NUMBER unit modifier
 *   unit     ::= 'h' | 'min' | 's' | 'sec' | 'km' | 'm'
 *   modifier ::= NUMBER '>' NUMBER '%' ( '@' INTEGER )?   RAMP   N→M%
 *              | NUMBER '%' ( '@' INTEGER )?              STEADY / INTERVAL   N%
 *              | 'W' ( NUMBER '%' )? ( '@' INTEGER )?     WARMUP
 *              | 'C' ( NUMBER '%' )? ( '@' INTEGER )?     COOLDOWN
 *              | 'P'                                      PAUSE
 *              | 'F' | 'Free'                             FREE
 *              | (none)                                   FREE
 *
 * Examples:
 *   10min85%                    → STEADY 600s @85%
 *   10min60>90%                 → RAMP 600s 60→90%
 *   5minP                       → PAUSE 300s
 *   15minW60%                   → WARMUP 900s @60%
 *   10minC55%                   → COOLDOWN 600s @55%
 *   10min85%@90                 → STEADY 600s @85% cadence 90rpm
 *   3*(3min105%-2minP)          → 3× [INTERVAL 180s @105%, PAUSE 120s]
 *   10min60%-5*(3min105%-2minP)-10minC55%
 */

// ── Types ─────────────────────────────────────────────────────────────────────

export type BlockType =
  | 'WARMUP'
  | 'STEADY'
  | 'INTERVAL'
  | 'COOLDOWN'
  | 'RAMP'
  | 'FREE'
  | 'PAUSE'
  | 'TRANSITION';

export interface WorkoutBlock {
  type: BlockType;
  durationSeconds?: number;
  distanceMeters?: number;
  label: string;
  intensityTarget?: number;
  intensityStart?: number;
  intensityEnd?: number;
  cadenceTarget?: number;
}

export interface NotationParseResult {
  blocks: WorkoutBlock[];
  totalDurationSeconds: number;
  /** Estimated Intensity Factor (0–1+) */
  estimatedIf: number;
  /** Estimated TSS */
  estimatedTss: number;
}

export class WorkoutNotationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WorkoutNotationError';
  }
}

// ── Token types ───────────────────────────────────────────────────────────────

type TT = 'NUMBER' | 'IDENT' | 'STAR' | 'LPAREN' | 'RPAREN' | 'DASH' | 'PCT' | 'GT' | 'AT' | 'EOF';

interface Token {
  type: TT;
  value: string;
}

// ── Parser ────────────────────────────────────────────────────────────────────

class NotationParser {
  private pos = 0;
  private current!: Token;

  constructor(private readonly input: string) {
    this.nextToken();
  }

  static parse(notation: string): WorkoutBlock[] {
    if (!notation || !notation.trim()) {
      throw new WorkoutNotationError('Notation must not be empty');
    }
    return new NotationParser(notation.trim()).parsePlan(false);
  }

  // ── Lexer ──────────────────────────────────────────────────────────────────

  private nextToken(): void {
    while (this.pos < this.input.length && this.input[this.pos] === ' ') this.pos++;

    if (this.pos >= this.input.length) {
      this.current = { type: 'EOF', value: '' };
      return;
    }

    const c = this.input[this.pos];

    if (/\d/.test(c)) {
      const s = this.pos;
      let dot = false;
      while (this.pos < this.input.length) {
        const ch = this.input[this.pos];
        if (/\d/.test(ch)) {
          this.pos++;
        } else if (
          ch === '.' &&
          !dot &&
          this.pos + 1 < this.input.length &&
          /\d/.test(this.input[this.pos + 1])
        ) {
          dot = true;
          this.pos++;
        } else {
          break;
        }
      }
      this.current = { type: 'NUMBER', value: this.input.slice(s, this.pos) };
      return;
    }

    if (/[a-zA-Z]/.test(c)) {
      const s = this.pos;
      const startsLower = /[a-z]/.test(c);
      this.pos++;
      while (this.pos < this.input.length && /[a-zA-Z]/.test(this.input[this.pos])) {
        // Break at lowercase→uppercase boundary so "minP" → "min" + "P"
        if (startsLower && /[A-Z]/.test(this.input[this.pos])) break;
        this.pos++;
      }
      this.current = { type: 'IDENT', value: this.input.slice(s, this.pos) };
      return;
    }

    this.pos++;
    const single: Record<string, TT> = {
      '*': 'STAR',
      '(': 'LPAREN',
      ')': 'RPAREN',
      '-': 'DASH',
      '%': 'PCT',
      '>': 'GT',
      '@': 'AT',
    };
    if (c in single) {
      this.current = { type: single[c], value: c };
      return;
    }
    throw new WorkoutNotationError(`Unexpected character '${c}' at position ${this.pos - 1}`);
  }

  private peek(): Token {
    return this.current;
  }

  private consume(expected: TT): Token {
    if (this.current.type !== expected) {
      throw new WorkoutNotationError(
        `Expected ${expected} but found '${this.current.value}' near "${this.tail()}"`,
      );
    }
    const t = this.current;
    this.nextToken();
    return t;
  }

  // ── Parser ─────────────────────────────────────────────────────────────────

  private parsePlan(insideInterval: boolean): WorkoutBlock[] {
    const blocks: WorkoutBlock[] = [...this.parseSegment(insideInterval)];
    while (this.peek().type === 'DASH') {
      this.nextToken(); // consume '-'
      if (this.peek().type === 'RPAREN' || this.peek().type === 'EOF') break;
      blocks.push(...this.parseSegment(insideInterval));
    }
    return blocks;
  }

  private parseSegment(insideInterval: boolean): WorkoutBlock[] {
    if (this.peek().type !== 'NUMBER') {
      throw new WorkoutNotationError(`Expected a number near "${this.tail()}"`);
    }
    const numStr = this.peek().value;
    this.nextToken(); // consume NUMBER

    if (this.peek().type === 'STAR') {
      // interval: N*( plan )
      const count = parseInt(numStr, 10);
      if (isNaN(count) || count < 1) {
        throw new WorkoutNotationError(`Repeat count must be a positive integer, got: ${numStr}`);
      }
      this.nextToken(); // consume '*'
      this.consume('LPAREN');
      const inner = this.parsePlan(true);
      this.consume('RPAREN');
      const expanded: WorkoutBlock[] = [];
      for (let i = 0; i < count; i++) expanded.push(...inner);
      return expanded;
    }

    // block
    return [this.parseBlockBody(parseFloat(numStr), insideInterval)];
  }

  private parseBlockBody(dur: number, insideInterval: boolean): WorkoutBlock {
    const unit = this.consume('IDENT').value.toLowerCase();
    const durationSeconds = this.isTimeUnit(unit) ? this.toSeconds(dur, unit) : undefined;
    const distanceMeters = this.isDistanceUnit(unit) ? this.toDistanceMeters(dur, unit) : undefined;

    if (durationSeconds == null && distanceMeters == null) {
      throw new WorkoutNotationError(`Unknown unit: '${unit}'`);
    }
    return this.parseModifier(durationSeconds, distanceMeters, insideInterval);
  }

  private parseModifier(
    durationSeconds: number | undefined,
    distanceMeters: number | undefined,
    insideInterval: boolean,
  ): WorkoutBlock {
    // IDENT modifier: P, W, C, F
    if (this.peek().type === 'IDENT') {
      const id = this.peek().value.toUpperCase();
      this.nextToken();
      switch (id) {
        case 'P':
          return this.block(
            'PAUSE',
            durationSeconds,
            distanceMeters,
            undefined,
            undefined,
            undefined,
            undefined,
            'Recovery',
          );
        case 'F':
        case 'FREE':
          return this.block(
            'FREE',
            durationSeconds,
            distanceMeters,
            undefined,
            undefined,
            undefined,
            undefined,
            'Free',
          );
        case 'W': {
          const pct = this.consumeOptionalPct();
          const cadence = this.consumeOptionalCadence();
          return this.block(
            'WARMUP',
            durationSeconds,
            distanceMeters,
            pct,
            undefined,
            undefined,
            cadence,
            'Warm-Up' + this.pctSuffix(pct),
          );
        }
        case 'C': {
          const pct = this.consumeOptionalPct();
          const cadence = this.consumeOptionalCadence();
          return this.block(
            'COOLDOWN',
            durationSeconds,
            distanceMeters,
            pct,
            undefined,
            undefined,
            cadence,
            'Cool-Down' + this.pctSuffix(pct),
          );
        }
        default:
          throw new WorkoutNotationError(`Unknown modifier: '${id}'. Expected P, W, C, F, or Free`);
      }
    }

    // Numeric intensity: N% or N>M%
    if (this.peek().type === 'NUMBER') {
      const n1 = parseFloat(this.consume('NUMBER').value);

      if (this.peek().type === 'GT') {
        this.nextToken(); // consume '>'
        const end = Math.round(parseFloat(this.consume('NUMBER').value));
        this.consume('PCT');
        const cadence = this.consumeOptionalCadence();
        const start = Math.round(n1);
        return this.block(
          'RAMP',
          durationSeconds,
          distanceMeters,
          undefined,
          start,
          end,
          cadence,
          `Ramp ${start}→${end}%`,
        );
      }

      // STEADY or INTERVAL
      this.consume('PCT');
      const cadence = this.consumeOptionalCadence();
      const intensity = Math.round(n1);
      const type: BlockType = insideInterval ? 'INTERVAL' : 'STEADY';
      const label = `${insideInterval ? 'Interval' : 'Steady'} ${intensity}%`;
      return this.block(
        type,
        durationSeconds,
        distanceMeters,
        intensity,
        undefined,
        undefined,
        cadence,
        label,
      );
    }

    // No modifier → FREE
    return this.block(
      'FREE',
      durationSeconds,
      distanceMeters,
      undefined,
      undefined,
      undefined,
      undefined,
      'Free',
    );
  }

  // ── Optional clause helpers ────────────────────────────────────────────────

  private consumeOptionalPct(): number | undefined {
    if (this.peek().type === 'NUMBER') {
      const val = Math.round(parseFloat(this.consume('NUMBER').value));
      this.consume('PCT');
      return val;
    }
    return undefined;
  }

  private consumeOptionalCadence(): number | undefined {
    if (this.peek().type === 'AT') {
      this.nextToken(); // consume '@'
      return Math.round(parseFloat(this.consume('NUMBER').value));
    }
    return undefined;
  }

  // ── Unit helpers ───────────────────────────────────────────────────────────

  private isTimeUnit(u: string): boolean {
    return ['h', 'min', 's', 'sec'].includes(u);
  }

  private isDistanceUnit(u: string): boolean {
    return ['km', 'm'].includes(u);
  }

  private toSeconds(dur: number, unit: string): number {
    switch (unit) {
      case 'h':
        return Math.round(dur * 3600);
      case 'min':
        return Math.round(dur * 60);
      case 's':
      case 'sec':
        return Math.round(dur);
      default:
        return 0;
    }
  }

  private toDistanceMeters(dur: number, unit: string): number {
    switch (unit) {
      case 'km':
        return Math.round(dur * 1000);
      case 'm':
        return Math.round(dur);
      default:
        return 0;
    }
  }

  // ── Block factory ──────────────────────────────────────────────────────────

  private block(
    type: BlockType,
    durationSeconds: number | undefined,
    distanceMeters: number | undefined,
    intensityTarget: number | undefined,
    intensityStart: number | undefined,
    intensityEnd: number | undefined,
    cadenceTarget: number | undefined,
    label: string,
  ): WorkoutBlock {
    const b: WorkoutBlock = { type, label };
    if (durationSeconds != null) b.durationSeconds = durationSeconds;
    if (distanceMeters != null) b.distanceMeters = distanceMeters;
    if (intensityTarget != null) b.intensityTarget = intensityTarget;
    if (intensityStart != null) b.intensityStart = intensityStart;
    if (intensityEnd != null) b.intensityEnd = intensityEnd;
    if (cadenceTarget != null) b.cadenceTarget = cadenceTarget;
    return b;
  }

  private pctSuffix(pct?: number): string {
    return pct != null ? ` ${pct}%` : '';
  }

  private tail(): string {
    return this.input.slice(Math.min(this.pos, this.input.length), this.pos + 20);
  }
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Parse compact workout notation and return blocks + estimated metrics.
 *
 * @example
 * parseNotation('10min60%-5*(3min105%-2minP)-10minC55%')
 * // → { blocks: [...12 blocks...], totalDurationSeconds: 2100, estimatedIf: 0.82, estimatedTss: 40 }
 */
export function parseNotation(notation: string): NotationParseResult {
  const blocks = NotationParser.parse(notation);
  const totalDurationSeconds = blocks.reduce((s, b) => s + (b.durationSeconds ?? 0), 0);
  const estimatedIf = computeIF(blocks, totalDurationSeconds);
  const estimatedTss = computeTSS(totalDurationSeconds, estimatedIf);
  return { blocks, totalDurationSeconds, estimatedIf, estimatedTss };
}

// ── Metrics helpers ───────────────────────────────────────────────────────────

function computeIF(blocks: WorkoutBlock[], totalSec: number): number {
  if (totalSec === 0) return 0;
  let sumWeighted = 0;
  for (const b of blocks) {
    if (!b.durationSeconds) continue;
    const pct = effectivePct(b);
    sumWeighted += b.durationSeconds * pct * pct;
  }
  const raw = Math.sqrt(sumWeighted / totalSec) / 100;
  return Math.round(raw * 100) / 100;
}

function computeTSS(totalSec: number, estimatedIf: number): number {
  return Math.round((totalSec / 3600) * estimatedIf * estimatedIf * 100);
}

function effectivePct(b: WorkoutBlock): number {
  if (b.intensityTarget != null) return b.intensityTarget;
  if (b.intensityStart != null && b.intensityEnd != null) {
    return (b.intensityStart + b.intensityEnd) / 2;
  }
  switch (b.type) {
    case 'WARMUP':
    case 'COOLDOWN':
      return 65;
    case 'FREE':
      return 55;
    case 'PAUSE':
      return 0;
    default:
      return 70;
  }
}
