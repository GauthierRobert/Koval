package com.koval.trainingplannerbackend.training.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stateless recursive-descent parser for compact workout notation.
 * No Spring dependencies.
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
 *
 * <p>BlockType resolution:
 * <ul>
 *   <li>WORK context (inside Mx block) → INTERVAL</li>
 *   <li>REST context (after /R:) → STEADY</li>
 *   <li>STANDALONE context:
 *     WARM... → WARMUP, COOL... → COOLDOWN, P → PAUSE, F → FREE,
 *     FC/SC/BC → INTERVAL, otherwise → STEADY</li>
 * </ul>
 *
 * <p>Intensity: null for zone-code descriptors (resolved later by zone system);
 * integer for NUMBER% descriptors (already resolved).
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
     * Parses a complete notation string into a flat, expanded list of WorkoutBlocks.
     *
     * @throws CompactNotationException if the notation is invalid
     */
    public static List<WorkoutBlock> parse(String notation) {
        if (notation == null || notation.isBlank()) {
            throw new CompactNotationException("Notation must not be blank");
        }
        return new CompactNotationParser(notation.trim()).parsePlan();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Grammar rules
    // ─────────────────────────────────────────────────────────────────────────

    private List<WorkoutBlock> parsePlan() {
        List<WorkoutBlock> result = new ArrayList<>(parseSection());
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
     *
     * <p>Discriminated by lookahead:
     * <ul>
     *   <li>INT 'x(' ... → outer_set</li>
     *   <li>INT 'x' DIGIT → inline_reps</li>
     *   <li>otherwise → standalone_block</li>
     * </ul>
     */
    private List<WorkoutBlock> parseSection() {
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
                    return parseOuterSetBody(n);
                } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    return parseInlineRepsBody(n);
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
     * Called after consuming N and 'x('. Parses inner content and optional /r:time,
     * then expands into N × (inner + optional PAUSE).
     */
    private List<WorkoutBlock> parseOuterSetBody(int n) {
        expect('(');
        List<WorkoutBlock> inner = parseInner();
        skipWs();
        expect(')');

        // Optional passive rest: /r:time
        skipWs();
        Integer pauseSeconds = null;
        if (pos < input.length() && input.charAt(pos) == '/') {
            int saved = pos;
            pos++;
            if (pos < input.length() && input.charAt(pos) == 'r'
                    && pos + 1 < input.length() && input.charAt(pos + 1) == ':') {
                pos += 2; // consume 'r:'
                pauseSeconds = parseTime();
            } else {
                pos = saved; // not a /r: — backtrack
            }
        }

        List<WorkoutBlock> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.addAll(inner);
            if (pauseSeconds != null) {
                result.add(pauseBlock(pauseSeconds));
            }
        }
        return result;
    }

    /**
     * inner ::= inline_reps | standalone_block
     * (Used inside parentheses; no nested outer_sets.)
     */
    private List<WorkoutBlock> parseInner() {
        skipWs();
        int saved = pos;
        if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            int n = parseInteger();
            skipWs();
            if (pos < input.length() && input.charAt(pos) == 'x') {
                pos++; // consume 'x'
                skipWs();
                if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    return parseInlineRepsBody(n);
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
     * Expands into N × [work, rest?].
     */
    private List<WorkoutBlock> parseInlineRepsBody(int n) {
        WorkoutBlock work = parseStandaloneBlock(BlockCtx.WORK);

        // Optional active rest: /R:
        WorkoutBlock rest = null;
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

        List<WorkoutBlock> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(work);
            if (rest != null) result.add(rest);
        }
        return result;
    }

    /**
     * standalone_block ::= NUMBER unit descriptor
     *
     * <p>Descriptor options:
     * <ul>
     *   <li>IDENT (letters + optional trailing digits) → zone code label, intensityTarget = null</li>
     *   <li>NUMBER '%' → direct percentage, intensityTarget = n, label = "n%"</li>
     *   <li>(none) → FREE block, label = null</li>
     * </ul>
     */
    private WorkoutBlock parseStandaloneBlock(BlockCtx ctx) {
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

        return new WorkoutBlock(type, durationSeconds, distanceMeters, label, intensityPct, null, null, null);
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

    /**
     * time ::= NUMBER "'" NUMBER? | NUMBER '"'
     * Returns total duration in seconds.
     */
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

    /** Parse one or more consecutive digits as a non-negative integer. */
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

    /**
     * Parse unit: km, sec, min, m, s, h (checked longest-first to avoid prefix conflicts).
     * Must immediately follow the number (no leading whitespace consumed by default,
     * but skipWs() is called defensively).
     */
    private String parseUnit() {
        skipWs();
        if (pos >= input.length()) {
            throw new CompactNotationException("Expected unit at position " + pos + " but got EOF");
        }
        // Check multi-char units first to avoid prefix collisions (m vs min, s vs sec)
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

    /** Parse an identifier: letters and digits (e.g. "FC", "E4", "WARM", "COOL"). */
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

    private static WorkoutBlock pauseBlock(int seconds) {
        return new WorkoutBlock(BlockType.PAUSE, seconds, null, "P", null, null, null, null);
    }
}
