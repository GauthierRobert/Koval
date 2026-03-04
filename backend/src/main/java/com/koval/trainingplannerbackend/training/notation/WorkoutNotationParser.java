package com.koval.trainingplannerbackend.training.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for compact workout notation.
 *
 * <h3>Grammar (EBNF)</h3>
 * <pre>
 *   plan     ::= segment ( '-' segment )*
 *   segment  ::= interval | block
 *   interval ::= INTEGER '*(' plan ')'
 *   block    ::= NUMBER unit modifier
 *   unit     ::= 'h' | 'min' | 's' | 'sec' | 'km' | 'm'
 *   modifier ::= NUMBER '>' NUMBER '%' ( '@' INTEGER )?   RAMP   N→M%
 *              | NUMBER '%' ( '@' INTEGER )?              STEADY/INTERVAL   N%
 *              | 'W' ( NUMBER '%' )? ( '@' INTEGER )?     WARMUP
 *              | 'C' ( NUMBER '%' )? ( '@' INTEGER )?     COOLDOWN
 *              | 'P'                                      PAUSE
 *              | 'F' | 'Free'                             FREE
 *              | (none)                                   FREE
 * </pre>
 *
 * <h3>Examples</h3>
 * <pre>
 *   10min85%                   → STEADY 600s @85%
 *   10min60>90%                → RAMP 600s 60→90%
 *   5minP                      → PAUSE 300s
 *   15minW60%                  → WARMUP 900s @60%
 *   10minC55%                  → COOLDOWN 600s @55%
 *   10min85%@90                → STEADY 600s @85% cadence 90
 *   3*(3min105%-2minP)         → 3× [INTERVAL 180s @105%, PAUSE 120s]
 *   10min60%-5*(3min105%-2minP)-10minC55%
 * </pre>
 *
 * <p>This class is <b>stateless</b> (use {@link #parse(String)}) and has no Spring dependencies.
 */
public class WorkoutNotationParser {

    // ── Token types ──────────────────────────────────────────────────────────

    private enum TT { NUMBER, IDENT, STAR, LPAREN, RPAREN, DASH, PCT, GT, AT, EOF }

    private record Token(TT type, String value) {}

    // ── Parser state ─────────────────────────────────────────────────────────

    private final String input;
    private int pos;
    private Token current;

    private WorkoutNotationParser(String input) {
        this.input = input.trim();
        this.pos = 0;
        nextToken(); // prime the first token
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Parse {@code notation} and return a flat, ordered list of {@link WorkoutBlock}s.
     * Intervals are fully expanded (n × inner blocks).
     *
     * @throws WorkoutNotationException if the notation is malformed
     */
    public static List<WorkoutBlock> parse(String notation) {
        if (notation == null || notation.isBlank())
            throw new WorkoutNotationException("Notation must not be empty");
        return new WorkoutNotationParser(notation).parsePlan(false);
    }

    // ── Lexer ────────────────────────────────────────────────────────────────

    private void nextToken() {
        // skip spaces
        while (pos < input.length() && input.charAt(pos) == ' ') pos++;

        if (pos >= input.length()) {
            current = new Token(TT.EOF, "");
            return;
        }

        char c = input.charAt(pos);

        // NUMBER: digits with at most one decimal point
        if (Character.isDigit(c)) {
            int start = pos;
            boolean dot = false;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isDigit(ch)) {
                    pos++;
                } else if (ch == '.' && !dot && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            current = new Token(TT.NUMBER, input.substring(start, pos));
            return;
        }

        // IDENT: letters only
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
            current = new Token(TT.IDENT, input.substring(start, pos));
            return;
        }

        // Single-character tokens
        pos++;
        current = switch (c) {
            case '*' -> new Token(TT.STAR,   "*");
            case '(' -> new Token(TT.LPAREN, "(");
            case ')' -> new Token(TT.RPAREN, ")");
            case '-' -> new Token(TT.DASH,   "-");
            case '%' -> new Token(TT.PCT,    "%");
            case '>' -> new Token(TT.GT,     ">");
            case '@' -> new Token(TT.AT,     "@");
            default  -> throw new WorkoutNotationException(
                    "Unexpected character '" + c + "' at position " + (pos - 1));
        };
    }

    private Token peek() { return current; }

    private Token consume(TT expected) {
        if (current.type() != expected)
            throw new WorkoutNotationException(
                    "Expected " + expected + " but found '" + current.value()
                            + "' near \"" + tail() + "\"");
        Token t = current;
        nextToken();
        return t;
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    /**
     * plan ::= segment ( '-' segment )*
     *
     * @param insideInterval {@code true} when nested inside a {@code N*(...)} group;
     *                       affects whether N% blocks become INTERVAL vs STEADY.
     */
    private List<WorkoutBlock> parsePlan(boolean insideInterval) {
        List<WorkoutBlock> blocks = new ArrayList<>();
        blocks.addAll(parseSegment(insideInterval));

        while (peek().type() == TT.DASH) {
            nextToken(); // consume '-'
            // Trailing dash or dash before ')' → stop gracefully
            if (peek().type() == TT.RPAREN || peek().type() == TT.EOF) break;
            blocks.addAll(parseSegment(insideInterval));
        }
        return blocks;
    }

    /**
     * segment ::= interval | block
     * interval ::= INTEGER '*(' plan ')'
     */
    private List<WorkoutBlock> parseSegment(boolean insideInterval) {
        if (peek().type() != TT.NUMBER)
            throw new WorkoutNotationException(
                    "Expected a number near \"" + tail() + "\"");

        String numStr = peek().value();
        nextToken(); // consume NUMBER — now peek at the token AFTER it

        if (peek().type() == TT.STAR) {
            // ── Interval: N*( plan ) ────────────────────────────────────
            int count;
            try { count = Integer.parseInt(numStr); }
            catch (NumberFormatException e) {
                throw new WorkoutNotationException(
                        "Repeat count must be an integer, got: " + numStr);
            }
            if (count < 1)
                throw new WorkoutNotationException("Repeat count must be ≥ 1");

            nextToken();             // consume '*'
            consume(TT.LPAREN);
            List<WorkoutBlock> inner = parsePlan(true);
            consume(TT.RPAREN);

            List<WorkoutBlock> expanded = new ArrayList<>(inner.size() * count);
            for (int i = 0; i < count; i++) expanded.addAll(inner);
            return expanded;
        }

        // ── Block: NUMBER was the duration ─────────────────────────────
        return List.of(parseBlockBody(Double.parseDouble(numStr), insideInterval));
    }

    /** Parse unit + modifier after the duration number has already been consumed. */
    private WorkoutBlock parseBlockBody(double dur, boolean insideInterval) {
        String unit = consume(TT.IDENT).value().toLowerCase();
        Integer durationSec    = isTimeUnit(unit)     ? toSeconds(dur, unit)       : null;
        Integer distanceMeters = isDistanceUnit(unit) ? toDistanceMeters(dur, unit) : null;

        if (durationSec == null && distanceMeters == null)
            throw new WorkoutNotationException("Unknown unit: '" + unit + "'");

        return parseModifier(durationSec, distanceMeters, insideInterval);
    }

    /**
     * modifier ::= N>M%[@cadence]   RAMP
     *            | N%[@cadence]     STEADY / INTERVAL
     *            | W[N%][@cadence]  WARMUP
     *            | C[N%][@cadence]  COOLDOWN
     *            | P                PAUSE
     *            | F | Free         FREE
     *            | (none)           FREE
     */
    private WorkoutBlock parseModifier(Integer durationSec, Integer distanceMeters,
                                       boolean insideInterval) {
        // ── IDENT modifier: P, W, C, F / Free ───────────────────────────
        if (peek().type() == TT.IDENT) {
            String id = peek().value();
            nextToken(); // consume IDENT
            return switch (id.toUpperCase()) {
                case "P" ->
                        block(BlockType.PAUSE, durationSec, distanceMeters,
                              null, null, null, null, "Recovery");
                case "F", "FREE" ->
                        block(BlockType.FREE, durationSec, distanceMeters,
                              null, null, null, null, "Free");
                case "W" -> {
                    Integer pct     = consumeOptionalPct();
                    Integer cadence = consumeOptionalCadence();
                    yield block(BlockType.WARMUP, durationSec, distanceMeters,
                                pct, null, null, cadence,
                                "Warm-Up" + pctSuffix(pct));
                }
                case "C" -> {
                    Integer pct     = consumeOptionalPct();
                    Integer cadence = consumeOptionalCadence();
                    yield block(BlockType.COOLDOWN, durationSec, distanceMeters,
                                pct, null, null, cadence,
                                "Cool-Down" + pctSuffix(pct));
                }
                default -> throw new WorkoutNotationException(
                        "Unknown modifier: '" + id + "'. Expected P, W, C, F, or Free");
            };
        }

        // ── Numeric intensity: N% or N>M% ───────────────────────────────
        if (peek().type() == TT.NUMBER) {
            double n1 = Double.parseDouble(consume(TT.NUMBER).value());

            if (peek().type() == TT.GT) {
                // RAMP: N>M%
                nextToken(); // consume '>'
                int end = (int) Double.parseDouble(consume(TT.NUMBER).value());
                consume(TT.PCT);
                Integer cadence = consumeOptionalCadence();
                int start = (int) n1;
                return block(BlockType.RAMP, durationSec, distanceMeters,
                             null, start, end, cadence,
                             "Ramp " + start + "→" + end + "%");
            }

            // STEADY or INTERVAL: N%
            consume(TT.PCT);
            Integer cadence  = consumeOptionalCadence();
            int intensity    = (int) n1;
            BlockType type   = insideInterval ? BlockType.INTERVAL : BlockType.STEADY;
            String lbl       = (insideInterval ? "Interval " : "Steady ") + intensity + "%";
            return block(type, durationSec, distanceMeters,
                         intensity, null, null, cadence, lbl);
        }

        // ── No modifier → FREE ───────────────────────────────────────────
        return block(BlockType.FREE, durationSec, distanceMeters,
                     null, null, null, null, "Free");
    }

    // ── Optional clause helpers ──────────────────────────────────────────────

    /** Consume {@code NUMBER '%'} if present, returning the integer value. */
    private Integer consumeOptionalPct() {
        if (peek().type() == TT.NUMBER) {
            int val = (int) Double.parseDouble(consume(TT.NUMBER).value());
            consume(TT.PCT);
            return val;
        }
        return null;
    }

    /** Consume {@code '@' NUMBER} if present, returning the cadence. */
    private Integer consumeOptionalCadence() {
        if (peek().type() == TT.AT) {
            nextToken(); // consume '@'
            return (int) Double.parseDouble(consume(TT.NUMBER).value());
        }
        return null;
    }

    // ── Unit helpers ─────────────────────────────────────────────────────────

    private boolean isTimeUnit(String u) {
        return u.equals("h") || u.equals("min") || u.equals("s") || u.equals("sec");
    }

    private boolean isDistanceUnit(String u) {
        return u.equals("km") || u.equals("m");
    }

    private int toSeconds(double dur, String unit) {
        return switch (unit) {
            case "h"        -> (int) (dur * 3600);
            case "min"      -> (int) (dur * 60);
            case "s", "sec" -> (int) dur;
            default         -> 0;
        };
    }

    private int toDistanceMeters(double dur, String unit) {
        return switch (unit) {
            case "km" -> (int) (dur * 1000);
            case "m"  -> (int) dur;
            default   -> 0;
        };
    }

    // ── Block factory ────────────────────────────────────────────────────────

    private WorkoutBlock block(BlockType type,
                               Integer durationSec, Integer distanceMeters,
                               Integer intensityTarget,
                               Integer intensityStart, Integer intensityEnd,
                               Integer cadence, String label) {
        return new WorkoutBlock(type, durationSec, distanceMeters, label,
                                intensityTarget, intensityStart, intensityEnd, cadence);
    }

    private String pctSuffix(Integer pct) {
        return pct != null ? " " + pct + "%" : "";
    }

    private String tail() {
        int end = Math.min(pos + 20, input.length());
        return input.substring(Math.min(pos, input.length()), end);
    }
}
