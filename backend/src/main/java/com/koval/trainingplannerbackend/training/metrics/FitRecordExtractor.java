package com.koval.trainingplannerbackend.training.metrics;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal FIT file reader that extracts the per-record speed and altitude streams from
 * .fit binaries. Mirrors the parser pattern in {@link FitPowerExtractor}.
 *
 * <p>Decoded fields (record message, global #20):
 * <ul>
 *   <li>field 6 (speed, uint16 ×1000) and field 73 (enhanced_speed, uint32 ×1000)</li>
 *   <li>field 2 (altitude, uint16 ×5 −500) and field 78 (enhanced_altitude, uint32 ×5 −500)</li>
 * </ul>
 * Enhanced fields are preferred when present.
 *
 * <p>Robustness contract: any malformed input causes {@link #extract(byte[])} to return
 * an empty {@link Samples} rather than throw.
 */
public final class FitRecordExtractor {

    /** Per-record samples in m/s and meters. Lists are aligned: {@code speedMps[i]} matches
     * {@code altitudeMeters[i]}. {@code Double.NaN} marks records missing altitude. Records
     * missing speed are dropped (speed is the primary signal). */
    public record Samples(List<Double> speedMps, List<Double> altitudeMeters) {
        public boolean isEmpty() {
            return speedMps.isEmpty();
        }
    }

    private static final int RECORD_GLOBAL_MSG = 20;
    private static final int FIELD_ALTITUDE = 2;
    private static final int FIELD_SPEED = 6;
    private static final int FIELD_ENHANCED_SPEED = 73;
    private static final int FIELD_ENHANCED_ALTITUDE = 78;
    private static final int U16_INVALID = 0xFFFF;
    private static final long U32_INVALID = 0xFFFFFFFFL;
    private static final double SPEED_SCALE = 1000.0;
    private static final double ALTITUDE_SCALE = 5.0;
    private static final double ALTITUDE_OFFSET = 500.0;

    private FitRecordExtractor() {}

    private static final class Definition {
        int globalMsgNum;
        ByteOrder byteOrder;
        int totalSize;
        int speedOffset = -1;
        int speedSize = 0;
        int altitudeOffset = -1;
        int altitudeSize = 0;
    }

    /**
     * Extract per-record speed and altitude samples from a FIT file.
     *
     * @param fitBytes raw FIT binary contents
     * @return aligned speed/altitude lists, empty when no data or input malformed
     */
    public static Samples extract(byte[] fitBytes) {
        if (fitBytes == null || fitBytes.length < 14) return empty();

        int headerSize = fitBytes[0] & 0xFF;
        if (headerSize != 12 && headerSize != 14) return empty();
        if (fitBytes.length < headerSize + 2) return empty();
        if (fitBytes[8] != '.' || fitBytes[9] != 'F' || fitBytes[10] != 'I' || fitBytes[11] != 'T') {
            return empty();
        }
        long dataSize = readUint32LE(fitBytes, 4);
        int dataEnd = headerSize + (int) dataSize;
        if (dataEnd > fitBytes.length) return empty();

        Definition[] defs = new Definition[16];
        List<Double> speeds = new ArrayList<>();
        List<Double> altitudes = new ArrayList<>();
        int pos = headerSize;

        try {
            while (pos < dataEnd) {
                int hdr = fitBytes[pos++] & 0xFF;
                if ((hdr & 0x80) != 0) {
                    int localType = (hdr >> 5) & 0x3;
                    Definition def = defs[localType];
                    if (def == null) return empty();
                    captureRecord(fitBytes, pos, def, speeds, altitudes);
                    pos += def.totalSize;
                } else {
                    int localType = hdr & 0x0F;
                    boolean isDefinition = (hdr & 0x40) != 0;
                    boolean hasDevData = (hdr & 0x20) != 0;
                    if (isDefinition) {
                        pos++; // reserved
                        int arch = fitBytes[pos++] & 0xFF;
                        ByteOrder order = arch == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                        int globalMsgNum = readUint16(fitBytes, pos, order);
                        pos += 2;
                        int numFields = fitBytes[pos++] & 0xFF;
                        Definition def = new Definition();
                        def.globalMsgNum = globalMsgNum;
                        def.byteOrder = order;
                        int offset = 0;
                        for (int i = 0; i < numFields; i++) {
                            int fieldDefNum = fitBytes[pos++] & 0xFF;
                            int size = fitBytes[pos++] & 0xFF;
                            pos++; // base type
                            if (globalMsgNum == RECORD_GLOBAL_MSG) {
                                rememberFieldOffset(def, fieldDefNum, size, offset);
                            }
                            offset += size;
                        }
                        if (hasDevData) {
                            int numDev = fitBytes[pos++] & 0xFF;
                            for (int i = 0; i < numDev; i++) {
                                pos++; // dev field num
                                int devSize = fitBytes[pos++] & 0xFF;
                                pos++; // dev data index
                                offset += devSize;
                            }
                        }
                        def.totalSize = offset;
                        defs[localType] = def;
                    } else {
                        Definition def = defs[localType];
                        if (def == null) return empty();
                        captureRecord(fitBytes, pos, def, speeds, altitudes);
                        pos += def.totalSize;
                    }
                }
            }
        } catch (Exception e) {
            return empty();
        }
        return new Samples(speeds, altitudes);
    }

    private static void rememberFieldOffset(Definition def, int fieldDefNum, int size, int offset) {
        if (fieldDefNum == FIELD_ENHANCED_SPEED && size == 4) {
            def.speedOffset = offset;
            def.speedSize = 4;
        } else if (fieldDefNum == FIELD_SPEED && size == 2 && def.speedSize != 4) {
            def.speedOffset = offset;
            def.speedSize = 2;
        } else if (fieldDefNum == FIELD_ENHANCED_ALTITUDE && size == 4) {
            def.altitudeOffset = offset;
            def.altitudeSize = 4;
        } else if (fieldDefNum == FIELD_ALTITUDE && size == 2 && def.altitudeSize != 4) {
            def.altitudeOffset = offset;
            def.altitudeSize = 2;
        }
    }

    private static void captureRecord(byte[] b, int basePos, Definition def,
                                      List<Double> speeds, List<Double> altitudes) {
        if (def.globalMsgNum != RECORD_GLOBAL_MSG) return;
        if (def.speedOffset < 0) return;
        double speed = readSpeed(b, basePos + def.speedOffset, def.speedSize, def.byteOrder);
        if (Double.isNaN(speed)) return;
        speeds.add(speed);
        double alt = def.altitudeOffset >= 0
                ? readAltitude(b, basePos + def.altitudeOffset, def.altitudeSize, def.byteOrder)
                : Double.NaN;
        altitudes.add(alt);
    }

    private static double readSpeed(byte[] b, int pos, int size, ByteOrder order) {
        if (size == 2) {
            int raw = readUint16(b, pos, order);
            return raw == U16_INVALID ? Double.NaN : raw / SPEED_SCALE;
        }
        long raw = readUint32(b, pos, order);
        return raw == U32_INVALID ? Double.NaN : raw / SPEED_SCALE;
    }

    private static double readAltitude(byte[] b, int pos, int size, ByteOrder order) {
        if (size == 2) {
            int raw = readUint16(b, pos, order);
            return raw == U16_INVALID ? Double.NaN : raw / ALTITUDE_SCALE - ALTITUDE_OFFSET;
        }
        long raw = readUint32(b, pos, order);
        return raw == U32_INVALID ? Double.NaN : raw / ALTITUDE_SCALE - ALTITUDE_OFFSET;
    }

    private static long readUint32LE(byte[] b, int pos) {
        return (b[pos] & 0xFFL)
                | ((b[pos + 1] & 0xFFL) << 8)
                | ((b[pos + 2] & 0xFFL) << 16)
                | ((b[pos + 3] & 0xFFL) << 24);
    }

    private static int readUint16(byte[] b, int pos, ByteOrder order) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8);
        }
        return ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
    }

    private static long readUint32(byte[] b, int pos, ByteOrder order) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return (b[pos] & 0xFFL)
                    | ((b[pos + 1] & 0xFFL) << 8)
                    | ((b[pos + 2] & 0xFFL) << 16)
                    | ((b[pos + 3] & 0xFFL) << 24);
        }
        return ((b[pos] & 0xFFL) << 24)
                | ((b[pos + 1] & 0xFFL) << 16)
                | ((b[pos + 2] & 0xFFL) << 8)
                | (b[pos + 3] & 0xFFL);
    }

    private static Samples empty() {
        return new Samples(List.of(), List.of());
    }
}
