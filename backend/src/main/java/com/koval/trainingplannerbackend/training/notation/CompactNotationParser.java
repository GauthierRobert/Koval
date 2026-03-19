package com.koval.trainingplannerbackend.training.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stateless recursive-descent parser for compact workout notation.
 * Returns a TREE of {@link WorkoutElement} nodes — sets are NOT expanded.
 * Use {@link com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener}
 * to expand into a flat sequential list.
 *
 * <p>Grammar:
 * <pre>
 *   plan          ::= section ('+' section)*
 *   section       ::= outer_set | inline_reps | standalone_block
 *   outer_set     ::= INT 'x(' inner ')' ('/' 'r:' time)?
 *   inner         ::= inline_reps | standalone_block
 *   inline_reps   ::= INT 'x' standalone_block ('/' 'R:' standalone_block)?
 *   standalone_block ::= NUMBER unit descriptor
 *   unit          ::= 'm' | 'km' | 's' | 'sec' | 'min' | 'h'
 *   descriptor    ::= IDENT NUMBER? | NUMBER '%' | (none → FREE)
 *   time          ::= NUMBER "'" NUMBER? | NUMBER '"'
 * </pre>
 */
public final class CompactNotationParser {

    /** High-effort codes that map to INTERVAL when used standalone. */
    private static final Set<String> INTERVAL_CODES = Set.of("FC", "SC", "BC");

    private final String input;
    private int pos;

    private CompactNotationParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses a complete notation string into a tree of WorkoutElements.
     * Sets are returned as single nodes with repetitions and children — NOT expanded.
     *
     * @throws CompactNotationException if the notation is invalid
     */
    public static List<WorkoutElement> parse(String notation) {
        if (notation == null || notation.isBlank()) {
            throw new CompactNotationException("Notation must not be blank");
        }
        return new CompactNotationParser(notation.trim()).parsePlan();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Grammar rules
    // ─────────────────────────────────────────────────────────────────────────

    private List<WorkoutElement> parsePlan() {
        List<WorkoutElement> result = new ArrayList<>(parseSection());
        skipWs();
        while (pos < input.length() && input.charAt(pos) == '+') {
            pos++; // consume '+'
            result.addAll(parseSection());
            skipWs();
        }
        if (pos < input.length()) {
            throw new CompactNotationException(
                    "Unexpected character at position " + pos + ": '" + input.charAt(pos) + "'");
        }
        return result;
    }

    /**
     * section ::= outer_set | inline_reps | standalone_block
     */
    private List<WorkoutElement> parseSection() {
        skipWs();
        if (pos >= input.length()) {
            throw new CompactNotationException("Expected section but reached end of input");
        }

        int saved = pos;
        if (Character.isDigit(input.charAt(pos))) {
            int n = parseInteger();
            skipWs();
            if (pos < input.length() && input.charAt(pos) == 'x') {
                pos++; // consume 'x'
                skipWs();
                if (pos < input.length() && input.charAt(pos) == '(') {
                    return List.of(parseOuterSetBody(n));
                } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    return List.of(parseInlineRepsBody(n));
                } else {
                    throw new CompactNotationException(
                            "Expected '(' or digit after 'x' at position " + pos);
                }
            }
            // Not a rep multiplier — backtrack and treat as standalone block
            pos = saved;
        }
        return List.of(parseStandaloneBlock(BlockCtx.STANDALONE));
    }

    /**
     * Called after consuming N and 'x('. Parses inner content and optional /r:time.
     * Returns ONE set node with repetitions=N and children.
     */
    private WorkoutElement parseOuterSetBody(int n) {
        expect('(');
        List<WorkoutElement> inner = parseInner();
        skipWs();
        expect(')');

        // Optional passive rest: /r:time
        skipWs();
        Integer restDurationSeconds = null;
        if (pos < input.length() && input.charAt(pos) == '/') {
            int saved = pos;
            pos++;
            if (pos < input.length() && input.charAt(pos) == 'r'
                    && pos + 1 < input.length() && input.charAt(pos + 1) == ':') {
                pos += 2; // consume 'r:'
                restDurationSeconds = parseTime();
            } else {
                pos = saved; // not a /r: — backtrack
            }
        }

        return setElement(n, inner, restDurationSeconds);
    }

    /**
     * inner ::= inline_reps | standalone_block
     * (Used inside parentheses; no nested outer_sets.)
     */
    private List<WorkoutElement> parseInner() {
        skipWs();
        int saved = pos;
        if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            int n = parseInteger();
            skipWs();
            if (pos < input.length() && input.charAt(pos) == 'x') {
                pos++; // consume 'x'
                skipWs();
                if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    return List.of(parseInlineRepsBody(n));
                }
                throw new CompactNotationException(
                        "Expected digit after 'x' inside parentheses at position " + pos);
            }
            pos = saved; // backtrack
        }
        return List.of(parseStandaloneBlock(BlockCtx.STANDALONE));
    }

    /**
     * Called after consuming N and 'x' for inline reps.
     * Parses: standalone_block (/R: standalone_block)?
     * Returns ONE set node with repetitions=N and [work, rest?] as children.
     */
    private WorkoutElement parseInlineRepsBody(int n) {
        WorkoutElement work = parseStandaloneBlock(BlockCtx.WORK);

        // Optional active rest: /R:
        WorkoutElement rest = null;
        skipWs();
        if (pos < input.length() && input.charAt(pos) == '/') {
            int saved = pos;
            pos++;
            if (pos < input.length() && input.charAt(pos) == 'R'
                    && pos + 1 < input.length() && input.charAt(pos + 1) == ':') {
                pos += 2; // consume 'R:'
                rest = parseStandaloneBlock(BlockCtx.REST);
            } else {
                pos = saved; // not a /R: — backtrack
            }
        }

        List<WorkoutElement> children = new ArrayList<>();
        children.add(work);
        if (rest != null) children.add(rest);

        return setElement(n, children, null);
    }

    /**
     * standalone_block ::= NUMBER unit descriptor
     */
    private WorkoutElement parseStandaloneBlock(BlockCtx ctx) {
        skipWs();
        int quantity = parseInteger();
        String unit = parseUnit();

        // Resolve to durationSeconds or distanceMeters
        Integer durationSeconds = null;
        Integer distanceMeters = null;
        switch (unit) {
            case "m"        -> distanceMeters = quantity;
            case "km"       -> distanceMeters = quantity * 1000;
            case "s", "sec" -> durationSeconds = quantity;
            case "min"      -> durationSeconds = quantity * 60;
            case "h"        -> durationSeconds = quantity * 3600;
            default         -> throw new CompactNotationException("Unknown unit: " + unit);
        }

        // Parse optional descriptor
        String descLabel = null;
        Integer intensityPct = null;

        if (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isLetter(c)) {
                descLabel = parseIdent();
            } else if (Character.isDigit(c)) {
                int pct = parseInteger();
                if (pos >= input.length() || input.charAt(pos) != '%') {
                    throw new CompactNotationException(
                            "Expected '%' after intensity number at position " + pos);
                }
                pos++; // consume '%'
                intensityPct = pct;
            }
        }

        BlockType type = resolveBlockType(ctx, descLabel, intensityPct);
        String label = descLabel != null ? descLabel : (intensityPct != null ? intensityPct + "%" : null);
        String zoneTarget = (intensityPct == null && descLabel != null) ? descLabel : null;

        return leafElement(type, durationSeconds, distanceMeters, label, intensityPct, zoneTarget);
    }

    /** Resolve BlockType from context and descriptor. */
    private static BlockType resolveBlockType(BlockCtx ctx, String descriptor, Integer intensityPct) {
        if (ctx == BlockCtx.WORK) return BlockType.INTERVAL;
        if (ctx == BlockCtx.REST) return BlockType.STEADY;

        // STANDALONE: infer from descriptor
        if (descriptor == null) {
            return intensityPct != null ? BlockType.STEADY : BlockType.FREE;
        }
        String upper = descriptor.toUpperCase();
        if (upper.startsWith("WARM")) return BlockType.WARMUP;
        if (upper.startsWith("COOL")) return BlockType.COOLDOWN;
        if (upper.equals("P"))        return BlockType.PAUSE;
        if (upper.equals("F"))        return BlockType.FREE;
        if (INTERVAL_CODES.contains(upper)) return BlockType.INTERVAL;
        return BlockType.STEADY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lexical helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int parseTime() {
        skipWs();
        int n = parseInteger();
        skipWs();
        if (pos < input.length() && input.charAt(pos) == '\'') {
            pos++; // consume "'"
            int seconds = n * 60;
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                seconds += parseInteger();
            }
            return seconds;
        } else if (pos < input.length() && input.charAt(pos) == '"') {
            pos++; // consume '"'
            return n; // n seconds
        } else {
            String got = pos < input.length() ? "'" + input.charAt(pos) + "'" : "EOF";
            throw new CompactNotationException(
                    "Expected \"'\" or '\"' in time expression at position " + pos + " but got " + got);
        }
    }

    private int parseInteger() {
        skipWs();
        if (pos >= input.length() || !Character.isDigit(input.charAt(pos))) {
            String ctx = pos < input.length() ? "'" + input.charAt(pos) + "'" : "EOF";
            throw new CompactNotationException("Expected integer at position " + pos + " but got " + ctx);
        }
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        return Integer.parseInt(input, start, pos, 10);
    }

    private String parseUnit() {
        skipWs();
        if (pos >= input.length()) {
            throw new CompactNotationException("Expected unit at position " + pos + " but got EOF");
        }
        if (startsWith("km"))  { pos += 2; return "km"; }
        if (startsWith("sec")) { pos += 3; return "sec"; }
        if (startsWith("min")) { pos += 3; return "min"; }
        char c = input.charAt(pos);
        if (c == 'm') { pos++; return "m"; }
        if (c == 's') { pos++; return "s"; }
        if (c == 'h') { pos++; return "h"; }
        throw new CompactNotationException(
                "Expected unit (m, km, s, sec, min, h) at position " + pos + " but got '" + c + "'");
    }

    private String parseIdent() {
        int start = pos;
        while (pos < input.length()
                && (Character.isLetter(input.charAt(pos)) || Character.isDigit(input.charAt(pos)))) {
            pos++;
        }
        if (pos == start) {
            throw new CompactNotationException("Expected identifier at position " + pos);
        }
        return input.substring(start, pos);
    }

    private boolean startsWith(String s) {
        return input.startsWith(s, pos);
    }

    private void expect(char c) {
        skipWs();
        if (pos >= input.length() || input.charAt(pos) != c) {
            String got = pos < input.length() ? "'" + input.charAt(pos) + "'" : "EOF";
            throw new CompactNotationException(
                    "Expected '" + c + "' at position " + pos + " but got " + got);
        }
        pos++;
    }

    private void skipWs() {
        while (pos < input.length() && input.charAt(pos) == ' ') {
            pos++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Block context enum
    // ─────────────────────────────────────────────────────────────────────────

    private enum BlockCtx { STANDALONE, WORK, REST }

    // ─────────────────────────────────────────────────────────────────────────
    //  Factory helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static WorkoutElement leafElement(BlockType type, Integer durationSeconds,
                                              Integer distanceMeters, String label,
                                              Integer intensityPct, String zoneTarget) {
        return new WorkoutElement(null, null, null, null,
                type, durationSeconds, distanceMeters, label, null,
                intensityPct, null, null, null, zoneTarget, null);
    }

    private static WorkoutElement setElement(int reps, List<WorkoutElement> children, Integer restDurationSeconds) {
        return new WorkoutElement(reps, children, restDurationSeconds, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
