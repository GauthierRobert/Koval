package com.koval.trainingplannerbackend.training.metrics;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal FIT file reader that extracts the per-record GPS track from .fit binaries.
 * Mirrors the parser pattern in {@link FitRecordExtractor} and {@link FitPowerExtractor}.
 *
 * <p>Decoded fields (record message, global #20):
 * <ul>
 *   <li>field 0 (position_lat, sint32 semicircles)</li>
 *   <li>field 1 (position_long, sint32 semicircles)</li>
 * </ul>
 * Semicircles are converted to degrees: {@code degrees = semicircles * 180 / 2^31}.
 * Records missing either coordinate (or carrying the FIT "invalid" sentinel) are dropped,
 * so the returned track contains only points with a full lat/long fix.
 *
 * <p>Robustness contract: any malformed input causes {@link #extract(byte[])} to return an
 * empty {@link Track} rather than throw.
 */
public final class FitGpsExtractor {

    /** Per-record GPS points in decimal degrees. {@code lat[i]} matches {@code lng[i]}. */
    public record Track(List<Double> lat, List<Double> lng) {
        public boolean isEmpty() {
            return lat.isEmpty();
        }

        public int size() {
            return lat.size();
        }
    }

    private static final int RECORD_GLOBAL_MSG = 20;
    private static final int FIELD_POSITION_LAT = 0;
    private static final int FIELD_POSITION_LONG = 1;
    private static final int SINT32_INVALID = 0x7FFFFFFF;
    private static final double DEGREES_PER_SEMICIRCLE = 180.0 / 2147483648.0;

    private FitGpsExtractor() {}

    private static final class Definition {
        int globalMsgNum;
        ByteOrder byteOrder;
        int totalSize;
        int latOffset = -1;
        int lngOffset = -1;
    }

    /**
     * Extract the GPS track from a FIT file.
     *
     * @param fitBytes raw FIT binary contents
     * @return aligned lat/lng lists in degrees, empty when no GPS data or input malformed
     */
    public static Track extract(byte[] fitBytes) {
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
        List<Double> lat = new ArrayList<>();
        List<Double> lng = new ArrayList<>();
        int pos = headerSize;

        try {
            while (pos < dataEnd) {
                int hdr = fitBytes[pos++] & 0xFF;
                if ((hdr & 0x80) != 0) {
                    int localType = (hdr >> 5) & 0x3;
                    Definition def = defs[localType];
                    if (def == null) return empty();
                    captureRecord(fitBytes, pos, def, lat, lng);
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
                        captureRecord(fitBytes, pos, def, lat, lng);
                        pos += def.totalSize;
                    }
                }
            }
        } catch (Exception e) {
            return empty();
        }
        return new Track(lat, lng);
    }

    private static void rememberFieldOffset(Definition def, int fieldDefNum, int size, int offset) {
        if (fieldDefNum == FIELD_POSITION_LAT && size == 4) {
            def.latOffset = offset;
        } else if (fieldDefNum == FIELD_POSITION_LONG && size == 4) {
            def.lngOffset = offset;
        }
    }

    private static void captureRecord(byte[] b, int basePos, Definition def,
                                      List<Double> lat, List<Double> lng) {
        if (def.globalMsgNum != RECORD_GLOBAL_MSG) return;
        if (def.latOffset < 0 || def.lngOffset < 0) return;
        int rawLat = readSint32(b, basePos + def.latOffset, def.byteOrder);
        int rawLng = readSint32(b, basePos + def.lngOffset, def.byteOrder);
        if (rawLat == SINT32_INVALID || rawLng == SINT32_INVALID) return;
        lat.add(rawLat * DEGREES_PER_SEMICIRCLE);
        lng.add(rawLng * DEGREES_PER_SEMICIRCLE);
    }

    private static int readSint32(byte[] b, int pos, ByteOrder order) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return (b[pos] & 0xFF)
                    | ((b[pos + 1] & 0xFF) << 8)
                    | ((b[pos + 2] & 0xFF) << 16)
                    | ((b[pos + 3] & 0xFF) << 24);
        }
        return ((b[pos] & 0xFF) << 24)
                | ((b[pos + 1] & 0xFF) << 16)
                | ((b[pos + 2] & 0xFF) << 8)
                | (b[pos + 3] & 0xFF);
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

    private static Track empty() {
        return new Track(List.of(), List.of());
    }
}
