package com.koval.trainingplannerbackend.training.metrics;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal FIT file reader that extracts the per-record power stream from .fit binaries.
 *
 * <p>The Garmin FIT format is a sequence of definition + data messages preceded by a 12- or
 * 14-byte header. This reader walks the message stream and decodes only the {@code record}
 * message power field (global message 20, field def 7, uint16). Every other field is skipped
 * by advancing the cursor — no other types are interpreted, no dependency added.
 *
 * <p>Robustness contract: any malformed input causes {@link #extractPower(byte[])} to return
 * an empty list rather than throw. The caller treats "no power data" and "parse failure"
 * identically (we just don't render the chart).
 */
public final class FitPowerExtractor {

    private static final int RECORD_GLOBAL_MSG = 20;
    private static final int POWER_FIELD_DEF = 7;
    private static final int POWER_INVALID = 0xFFFF;

    private FitPowerExtractor() {}

    /** Per-local-type field layout, captured when a definition message is parsed. */
    private static final class Definition {
        int globalMsgNum;
        ByteOrder byteOrder;
        int totalSize;
        int powerOffset = -1;
    }

    /**
     * Extract per-record power samples from a FIT file.
     *
     * @param fitBytes raw FIT binary contents
     * @return list of power values in watts (one per record), empty if no power data or malformed
     */
    public static List<Integer> extractPower(byte[] fitBytes) {
        if (fitBytes == null || fitBytes.length < 14) return List.of();

        int headerSize = fitBytes[0] & 0xFF;
        if (headerSize != 12 && headerSize != 14) return List.of();
        if (fitBytes.length < headerSize + 2) return List.of();
        if (fitBytes[8] != '.' || fitBytes[9] != 'F' || fitBytes[10] != 'I' || fitBytes[11] != 'T') {
            return List.of();
        }
        long dataSize = readUint32LE(fitBytes, 4);
        int dataEnd = headerSize + (int) dataSize;
        if (dataEnd > fitBytes.length) return List.of();

        Definition[] defs = new Definition[16];
        List<Integer> powers = new ArrayList<>();
        int pos = headerSize;

        try {
            while (pos < dataEnd) {
                int hdr = fitBytes[pos++] & 0xFF;
                if ((hdr & 0x80) != 0) {
                    // Compressed-timestamp header — always a data message; local type in bits 5-6
                    int localType = (hdr >> 5) & 0x3;
                    Definition def = defs[localType];
                    if (def == null) return List.of();
                    if (def.globalMsgNum == RECORD_GLOBAL_MSG && def.powerOffset >= 0) {
                        int p = readUint16(fitBytes, pos + def.powerOffset, def.byteOrder);
                        if (p != POWER_INVALID) powers.add(p);
                    }
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
                            if (globalMsgNum == RECORD_GLOBAL_MSG && fieldDefNum == POWER_FIELD_DEF && size == 2) {
                                def.powerOffset = offset;
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
                        if (def == null) return List.of();
                        if (def.globalMsgNum == RECORD_GLOBAL_MSG && def.powerOffset >= 0) {
                            int p = readUint16(fitBytes, pos + def.powerOffset, def.byteOrder);
                            if (p != POWER_INVALID) powers.add(p);
                        }
                        pos += def.totalSize;
                    }
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return powers;
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
}
