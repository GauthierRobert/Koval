package com.koval.trainingplannerbackend.integration.strava;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal FIT binary writer that builds valid .fit files from Strava streams data.
 *
 * <h3>FIT file format overview</h3>
 * A FIT file is a compact binary format designed by Garmin (ANT+/Dynastream).
 * It consists of:
 * <ol>
 *   <li>A 14-byte <b>file header</b> (size, protocol version, profile version, data size, ".FIT" signature, CRC)</li>
 *   <li>A sequence of <b>definition messages</b> and <b>data messages</b></li>
 *   <li>A 2-byte <b>CRC</b> over all data messages</li>
 * </ol>
 *
 * <h3>Message types used in this builder</h3>
 * <ul>
 *   <li><b>file_id</b>  (global msg 0)  — identifies the file type (activity)</li>
 *   <li><b>record</b>   (global msg 20) — per-second sensor data (power, HR, cadence, speed, altitude)</li>
 *   <li><b>event</b>    (global msg 21) — timer start/stop events for pause detection</li>
 *   <li><b>session</b>  (global msg 18) — session-level summary (elapsed time, timer time, averages)</li>
 *   <li><b>activity</b> (global msg 34) — top-level activity wrapper</li>
 * </ul>
 *
 * <h3>Local types</h3>
 * Each definition is assigned a "local type" (0–15) that subsequent data messages reference:
 * <ul>
 *   <li>Local 0 → file_id</li>
 *   <li>Local 1 → record</li>
 *   <li>Local 2 → session</li>
 *   <li>Local 3 → activity</li>
 *   <li>Local 4 → event</li>
 *   <li>Local 5 → lap</li>
 * </ul>
 *
 * <h3>Timestamps</h3>
 * FIT uses its own epoch: 1989-12-31T00:00:00Z (631065600 seconds before Unix epoch).
 * All timestamps in the file are stored as seconds since this FIT epoch.
 *
 * @see <a href="https://developer.garmin.com/fit/protocol/">Garmin FIT SDK</a>
 */
public class FitFileBuilder {

    /** Offset between Unix epoch (1970) and FIT epoch (1989-12-31). */
    private static final long FIT_EPOCH_OFFSET = 631065600L;

    /** Gaps in the time stream larger than this (seconds) are treated as pauses. */
    private static final int PAUSE_GAP_THRESHOLD = 20;

    // ── FIT base type codes (used in field definitions) ─────────────────────
    // Each field definition includes a base type that tells the parser how to
    // decode the bytes. The high bit (0x80) indicates a multi-byte type.

    private static final int ENUM    = 0x00;  // 1-byte enumeration
    private static final int UINT8   = 0x02;  // 1-byte unsigned integer
    private static final int UINT16  = 0x84;  // 2-byte unsigned integer (little-endian)
    private static final int UINT32  = 0x86;  // 4-byte unsigned integer (little-endian)
    private static final int UINT32Z = 0x8c;  // 4-byte unsigned integer, 0 = invalid

    /** Nibble-based CRC-16 lookup table for FIT file integrity checks. */
    private static final int[] CRC_TABLE = {
            0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
            0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400
    };

    /** Accumulates all definition and data message bytes (header and CRC added in {@link #finish()}). */
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    /** Field definitions indexed by local type, used by {@link #writeMsg} to know each field's byte size. */
    private final List<int[][]> defs = new ArrayList<>();

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Build a complete FIT binary from Strava per-second streams.
     *
     * @param streams              Strava streams keyed by type: "time", "watts", "heartrate",
     *                             "cadence", "velocity_smooth", "distance", "altitude"
     * @param sportType            sport identifier: "CYCLING", "RUNNING", or "SWIMMING"
     * @param startTime            activity start time (UTC)
     * @param totalDurationSeconds elapsed wall-clock duration (includes pauses)
     * @param movingTimeSeconds    timer duration excluding pauses; null to fall back to totalDurationSeconds
     * @param avgPower             session average power (W)
     * @param avgHR                session average heart rate (bpm)
     * @param avgCadence           session average cadence (rpm)
     * @param avgSpeed             session average speed (m/s)
     * @return complete FIT file bytes ready to be stored or downloaded
     */
    public byte[] buildFromStreams(Map<String, List<? extends Number>> streams,
                                  String sportType,
                                  LocalDateTime startTime,
                                  int totalDurationSeconds,
                                  Integer movingTimeSeconds,
                                  double avgPower, double avgHR, double avgCadence, double avgSpeed) {
        return buildFromStreams(streams, sportType, startTime, totalDurationSeconds, movingTimeSeconds,
                avgPower, avgHR, avgCadence, avgSpeed, List.of());
    }

    /**
     * Same as {@link #buildFromStreams(Map, String, LocalDateTime, int, Integer, double, double, double, double)}
     * but also emits per-lap data. For swim activities, Strava returns rest intervals as laps
     * with {@code distance = 0} — preserving them lets the frontend render rest blocks.
     */
    public byte[] buildFromStreams(Map<String, List<? extends Number>> streams,
                                  String sportType,
                                  LocalDateTime startTime,
                                  int totalDurationSeconds,
                                  Integer movingTimeSeconds,
                                  double avgPower, double avgHR, double avgCadence, double avgSpeed,
                                  List<Map<String, Object>> laps) {

        long startUnix = startTime.toEpochSecond(ZoneOffset.UTC);
        int startTs = toFitTs(startUnix);
        int endTs = startTs + totalDurationSeconds;
        int elapsedMs = totalDurationSeconds * 1000;
        int timerMs = (movingTimeSeconds != null ? movingTimeSeconds : totalDurationSeconds) * 1000;
        boolean cycling = "CYCLING".equals(sportType);
        int sport = fitSport(sportType);

        writeFileId(endTs);
        writeTimerEvent(startTs, true);
        writeRecords(streams, startTs, cycling);
        writeTimerEvent(endTs, false);
        writeLaps(laps, startTs, endTs, sport);
        writeSession(endTs, startTs, elapsedMs, timerMs, sportType, cycling, avgPower, avgHR, avgCadence, avgSpeed, totalDurationSeconds);
        writeActivity(endTs, elapsedMs);

        return finish();
    }

    // ── FIT message writers ─────────────────────────────────────────────────

    /**
     * Write the file_id message (global msg 0).
     * Identifies this file as a FIT activity file (type = 4).
     */
    private void writeFileId(int endTs) {
        defineMsg(0, 0, new int[][]{
                {4, 4, UINT32},   // time_created
                {1, 2, UINT16},   // manufacturer (255 = development)
                {2, 2, UINT16},   // product
                {3, 4, UINT32Z},  // serial_number
                {0, 1, ENUM}      // type (4 = activity)
        });
        writeMsg(0, new int[]{endTs, 255, 0, 0, 4});
    }

    /**
     * Write a timer event message (global msg 21).
     * Timer events mark pause/resume boundaries in the activity.
     *
     * @param ts    FIT timestamp of the event
     * @param start true for timer start, false for timer stop_all
     */
    private void writeTimerEvent(int ts, boolean start) {
        ensureEventDefined();
        // event=0 (timer), event_type: 0=start, 4=stop_all
        writeMsg(4, new int[]{ts, 0, start ? 0 : 4});
    }

    /** Define the event message (local type 4) once, on first use. */
    private boolean eventDefined = false;
    private void ensureEventDefined() {
        if (eventDefined) return;
        defineMsg(4, 21, new int[][]{
                {253, 4, UINT32},  // timestamp
                {0, 1, ENUM},     // event (0 = timer)
                {1, 1, ENUM}      // event_type (0 = start, 4 = stop_all)
        });
        eventDefined = true;
    }

    /**
     * Write all per-second record messages (global msg 20) from Strava streams.
     * Detects gaps > {@link #PAUSE_GAP_THRESHOLD} seconds and emits timer stop/start
     * event pairs around each gap so the frontend can identify paused periods.
     *
     * <p>Record fields vary by sport:</p>
     * <ul>
     *   <li>Cycling: timestamp, power (W), HR, cadence, speed, altitude</li>
     *   <li>Running/Swimming: timestamp, distance (cm), HR, cadence, speed, altitude</li>
     * </ul>
     */
    private void writeRecords(Map<String, List<? extends Number>> streams, int startTs, boolean cycling) {
        List<? extends Number> timeStream = streams.get("time");
        if (timeStream == null || timeStream.isEmpty()) return;

        defineRecordMsg(cycling);

        List<? extends Number> powerStream    = streams.get("watts");
        List<? extends Number> hrStream       = streams.get("heartrate");
        List<? extends Number> cadenceStream  = streams.get("cadence");
        List<? extends Number> velocityStream = streams.get("velocity_smooth");
        List<? extends Number> distanceStream = streams.get("distance");
        List<? extends Number> altitudeStream = streams.get("altitude");

        for (int i = 0; i < timeStream.size(); i++) {
            int ts = startTs + timeStream.get(i).intValue();
            emitPauseEventsIfGap(timeStream, i, startTs);
            writeOneRecord(cycling, ts, i, powerStream, hrStream, cadenceStream, velocityStream, distanceStream, altitudeStream);
        }
    }

    /**
     * Define the record message layout (local type 1, global msg 20).
     * Cycling records carry power; running/swimming carry cumulative distance instead.
     * Both include enhanced_altitude (field 78, scale=5, offset=500).
     */
    private void defineRecordMsg(boolean cycling) {
        if (cycling) {
            defineMsg(1, 20, new int[][]{
                    {253, 4, UINT32},  // timestamp
                    {7, 2, UINT16},    // power (W)
                    {3, 1, UINT8},     // heart_rate (bpm)
                    {4, 1, UINT8},     // cadence (rpm)
                    {6, 2, UINT16},    // speed (mm/s)
                    {78, 4, UINT32}    // enhanced_altitude ((m + 500) * 5)
            });
        } else {
            defineMsg(1, 20, new int[][]{
                    {253, 4, UINT32},  // timestamp
                    {0, 4, UINT32},    // distance (cm cumulative)
                    {3, 1, UINT8},     // heart_rate (bpm)
                    {4, 1, UINT8},     // cadence (rpm/spm)
                    {6, 2, UINT16},    // speed (mm/s)
                    {78, 4, UINT32}    // enhanced_altitude ((m + 500) * 5)
            });
        }
    }

    /**
     * If the gap between this record and the previous one exceeds the pause threshold,
     * emit a timer stop (at previous timestamp) and timer start (at current timestamp).
     */
    private void emitPauseEventsIfGap(List<? extends Number> timeStream, int i, int startTs) {
        if (i == 0) return;
        int gap = timeStream.get(i).intValue() - timeStream.get(i - 1).intValue();
        if (gap > PAUSE_GAP_THRESHOLD) {
            writeTimerEvent(startTs + timeStream.get(i - 1).intValue(), false);
            writeTimerEvent(startTs + timeStream.get(i).intValue(), true);
        }
    }

    /** Write a single per-second record data message. */
    private void writeOneRecord(boolean cycling, int ts, int i,
                                List<? extends Number> powerStream, List<? extends Number> hrStream,
                                List<? extends Number> cadenceStream, List<? extends Number> velocityStream,
                                List<? extends Number> distanceStream, List<? extends Number> altitudeStream) {
        int hr       = intAt(hrStream, i);
        int cad      = intAt(cadenceStream, i);
        int speedRaw = (int) Math.round(doubleAt(velocityStream, i) * 1000);
        int altRaw   = (int) Math.round((doubleAt(altitudeStream, i) + 500) * 5);

        if (cycling) {
            writeMsg(1, new int[]{ts, intAt(powerStream, i), hr, cad, speedRaw, altRaw});
        } else {
            int distCm = (int) Math.round(doubleAt(distanceStream, i) * 100);
            writeMsg(1, new int[]{ts, distCm, hr, cad, speedRaw, altRaw});
        }
    }

    /**
     * Write the session message (global msg 18).
     * Contains the key timing fields:
     * <ul>
     *   <li>field 7: total_elapsed_time (ms) — wall clock, includes pauses</li>
     *   <li>field 8: total_timer_time (ms)   — active time, excludes pauses</li>
     * </ul>
     */
    private void writeSession(int endTs, int startTs, int elapsedMs, int timerMs,
                              String sportType, boolean cycling,
                              double avgPower, double avgHR, double avgCadence,
                              double avgSpeed, int totalDurationSeconds) {
        int sport = fitSport(sportType);
        if (cycling) {
            writeCyclingSession(endTs, startTs, elapsedMs, timerMs, sport, avgPower, avgHR, avgCadence);
        } else {
            int totalDistCm = (int) Math.round(totalDurationSeconds * avgSpeed * 100);
            writeRunSwimSession(endTs, startTs, elapsedMs, timerMs, sport, totalDistCm, avgHR, avgCadence);
        }
    }

    /** Write session message for cycling (includes avg_power, field 19). */
    private void writeCyclingSession(int endTs, int startTs, int elapsedMs, int timerMs,
                                     int sport, double avgPower, double avgHR, double avgCadence) {
        defineMsg(2, 18, new int[][]{
                {253, 4, UINT32}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32},
                {0, 1, ENUM}, {1, 1, ENUM}, {5, 1, ENUM}, {6, 1, ENUM},
                {19, 2, UINT16}, {16, 1, UINT8}, {18, 1, UINT8},
                {25, 2, UINT16}, {26, 2, UINT16}
        });
        writeMsg(2, new int[]{
                endTs, startTs, elapsedMs, timerMs,
                9, 1, sport, 0,
                (int) avgPower, (int) avgHR, (int) avgCadence,
                0, 1
        });
    }

    /** Write session message for running/swimming (includes total_distance, field 9). */
    private void writeRunSwimSession(int endTs, int startTs, int elapsedMs, int timerMs,
                                     int sport, int totalDistCm, double avgHR, double avgCadence) {
        defineMsg(2, 18, new int[][]{
                {253, 4, UINT32}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32},
                {0, 1, ENUM}, {1, 1, ENUM}, {5, 1, ENUM}, {6, 1, ENUM},
                {9, 4, UINT32}, {16, 1, UINT8}, {18, 1, UINT8},
                {25, 2, UINT16}, {26, 2, UINT16}
        });
        writeMsg(2, new int[]{
                endTs, startTs, elapsedMs, timerMs,
                9, 1, sport, 0,
                totalDistCm, (int) avgHR, (int) avgCadence,
                0, 1
        });
    }

    /**
     * Write lap messages (global msg 19) from Strava laps.
     * Strava returns swim rests as laps with {@code distance = 0}; we preserve these so the
     * frontend can render them as REST blocks. Falls back to cumulative durations when
     * {@code start_date} is missing or unparseable on a lap.
     */
    private void writeLaps(List<Map<String, Object>> laps, int startTs, int endTs, int sport) {
        if (laps == null || laps.isEmpty()) return;

        defineMsg(5, 19, new int[][]{
                {253, 4, UINT32}, // timestamp (end of lap)
                {2, 4, UINT32},   // start_time
                {7, 4, UINT32},   // total_elapsed_time (ms)
                {8, 4, UINT32},   // total_timer_time (ms)
                {9, 4, UINT32},   // total_distance (cm)
                {15, 1, UINT8},   // avg_heart_rate (bpm)
                {17, 1, UINT8},   // avg_cadence (rpm/spm)
                {25, 1, ENUM}     // sport
        });

        int cursorTs = startTs;
        for (Map<String, Object> lap : laps) {
            int lapStartTs = lapStartTs(lap, cursorTs);
            int elapsedS = intValue(lap.get("elapsed_time"));
            int movingS = intValue(lap.get("moving_time"));
            int timerS = movingS > 0 ? movingS : elapsedS;
            int lapEndTs = Math.min(lapStartTs + Math.max(elapsedS, timerS), endTs);
            int distCm = (int) Math.round(doubleValue(lap.get("distance")) * 100);

            writeMsg(5, new int[]{
                    lapEndTs,
                    lapStartTs,
                    elapsedS * 1000,
                    timerS * 1000,
                    distCm,
                    (int) Math.round(doubleValue(lap.get("average_heartrate"))),
                    (int) Math.round(doubleValue(lap.get("average_cadence"))),
                    sport
            });

            cursorTs = lapEndTs;
        }
    }

    /** Parse a Strava lap's start_date (ISO-8601) or fall back to a running cursor. */
    private static int lapStartTs(Map<String, Object> lap, int fallbackTs) {
        Object startDate = lap.get("start_date");
        if (startDate instanceof String s) {
            try {
                return toFitTs(OffsetDateTime.parse(s).toEpochSecond());
            } catch (DateTimeParseException ignored) {
                // fall through to fallback
            }
        }
        return fallbackTs;
    }

    private static int intValue(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double doubleValue(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    /**
     * Write the activity message (global msg 34).
     * Top-level container that references the session and records the total elapsed time.
     */
    private void writeActivity(int endTs, int elapsedMs) {
        defineMsg(3, 34, new int[][]{
                {253, 4, UINT32},  // timestamp
                {0, 4, UINT32},    // total_timer_time (ms)
                {1, 2, UINT16},    // num_sessions
                {2, 1, ENUM},      // type (0 = manual)
                {3, 1, ENUM},      // event (26 = activity)
                {4, 1, ENUM}       // event_type (1 = stop)
        });
        writeMsg(3, new int[]{endTs, elapsedMs, 1, 0, 26, 1});
    }

    // ── Low-level FIT binary encoding ───────────────────────────────────────

    /**
     * Write a <b>definition message</b> for a given local type.
     * A definition tells the parser the structure (field number, byte size, base type)
     * of all subsequent data messages that use the same local type.
     *
     * @param localType    local message number (0–15), used to link definition to data messages
     * @param globalMsgNum FIT global message number (e.g., 0=file_id, 20=record, 18=session)
     * @param fields       array of {fieldDefNum, sizeInBytes, baseType} for each field
     */
    private void defineMsg(int localType, int globalMsgNum, int[][] fields) {
        while (defs.size() <= localType) defs.add(null);
        defs.set(localType, fields);

        bytes.write(0x40 | localType); // record header: bit 6 set = definition message
        bytes.write(0x00);             // reserved
        bytes.write(0x00);             // architecture: 0 = little-endian
        writeLE(globalMsgNum, 2);
        bytes.write(fields.length);
        for (int[] f : fields) {
            bytes.write(f[0]); // field definition number
            bytes.write(f[1]); // field size in bytes
            bytes.write(f[2]); // base type
        }
    }

    /**
     * Write a <b>data message</b> for a previously defined local type.
     * Values are written in the same order as the field definitions.
     *
     * @param localType must match a prior {@link #defineMsg} call
     * @param values    integer values for each field (written as little-endian per field size)
     */
    private void writeMsg(int localType, int[] values) {
        int[][] fields = defs.get(localType);
        bytes.write(localType); // record header: bit 6 clear = data message
        for (int i = 0; i < fields.length; i++) {
            writeLE(i < values.length ? values[i] : 0, fields[i][1]);
        }
    }

    /**
     * Assemble the final FIT binary: 14-byte header + data messages + 2-byte CRC.
     * The header includes its own CRC over the first 12 bytes.
     */
    private byte[] finish() {
        byte[] data = bytes.toByteArray();
        int dataSize = data.length;

        byte[] hdr = buildHeader(dataSize);

        int dataCrc = 0;
        for (byte b : data) dataCrc = fitCrc(dataCrc, b & 0xff);

        byte[] result = new byte[14 + dataSize + 2];
        System.arraycopy(hdr, 0, result, 0, 14);
        System.arraycopy(data, 0, result, 14, dataSize);
        result[14 + dataSize]     = (byte) (dataCrc & 0xff);
        result[14 + dataSize + 1] = (byte) ((dataCrc >> 8) & 0xff);
        return result;
    }

    /** Build the 14-byte FIT file header (protocol 2.0, profile 2000). */
    private byte[] buildHeader(int dataSize) {
        byte[] hdr = new byte[14];
        hdr[0] = 14;                           // header size
        hdr[1] = 0x20;                         // protocol version 2.0
        hdr[2] = (byte) 0xd0; hdr[3] = 0x07;  // profile version 2000 (little-endian)
        hdr[4] = (byte) (dataSize & 0xff);
        hdr[5] = (byte) ((dataSize >> 8) & 0xff);
        hdr[6] = (byte) ((dataSize >> 16) & 0xff);
        hdr[7] = (byte) ((dataSize >> 24) & 0xff);
        hdr[8] = '.'; hdr[9] = 'F'; hdr[10] = 'I'; hdr[11] = 'T';

        int hdrCrc = 0;
        for (int i = 0; i < 12; i++) hdrCrc = fitCrc(hdrCrc, hdr[i] & 0xff);
        hdr[12] = (byte) (hdrCrc & 0xff);
        hdr[13] = (byte) ((hdrCrc >> 8) & 0xff);
        return hdr;
    }

    /** Write a little-endian integer value of the given byte size. */
    private void writeLE(int value, int size) {
        for (int i = 0; i < size; i++) {
            bytes.write((value >> (i * 8)) & 0xff);
        }
    }

    /**
     * FIT CRC-16 update — processes one byte using a nibble-based lookup.
     * Applied to both the file header and the data payload.
     */
    private static int fitCrc(int crc, int b) {
        int tmp = CRC_TABLE[crc & 0xf];
        crc = ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[b & 0xf];
        tmp = CRC_TABLE[crc & 0xf];
        return ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[(b >> 4) & 0xf];
    }

    // ── Value conversion helpers ────────────────────────────────────────────

    /** Convert Unix epoch seconds to FIT epoch seconds. */
    private static int toFitTs(long unixSeconds) {
        return (int) Math.max(0, unixSeconds - FIT_EPOCH_OFFSET);
    }

    /** Map sport type string to FIT sport enum value. */
    private static int fitSport(String sport) {
        if ("RUNNING".equals(sport))  return 1;
        if ("SWIMMING".equals(sport)) return 5;
        return 2; // CYCLING
    }

    /** Safely read an integer from a nullable/short list, defaulting to 0. */
    private static int intAt(List<? extends Number> list, int index) {
        if (list == null || index >= list.size()) return 0;
        Number val = list.get(index);
        return val != null ? val.intValue() : 0;
    }

    /** Safely read a double from a nullable/short list, defaulting to 0. */
    private static double doubleAt(List<? extends Number> list, int index) {
        if (list == null || index >= list.size()) return 0;
        Number val = list.get(index);
        return val != null ? val.doubleValue() : 0;
    }
}
