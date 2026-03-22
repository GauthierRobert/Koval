package com.koval.trainingplannerbackend.integration.strava;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal FIT binary writer — builds valid .fit files from Strava streams data.
 */
public class FitFileBuilder {

    // FIT epoch: 1989-12-31T00:00:00Z
    private static final long FIT_EPOCH_OFFSET = 631065600L;

    // Base type codes
    private static final int ENUM = 0x00;
    private static final int UINT8 = 0x02;
    private static final int UINT16 = 0x84;
    private static final int UINT32 = 0x86;
    private static final int UINT32Z = 0x8c;

    // CRC-16 lookup table
    private static final int[] CRC_TABLE = {
            0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
            0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400
    };

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final List<int[][]> defs = new ArrayList<>(); // indexed by localType

    public byte[] buildFromStreams(Map<String, List<? extends Number>> streams,
                                  String sportType,
                                  LocalDateTime startTime,
                                  int totalDurationSeconds,
                                  double avgPower, double avgHR, double avgCadence, double avgSpeed) {

        long startUnix = startTime.toEpochSecond(ZoneOffset.UTC);
        int startTs = toFitTs(startUnix);
        int endTs = startTs + totalDurationSeconds;
        int elapsedMs = totalDurationSeconds * 1000;
        int sport = fitSport(sportType);
        boolean cycling = "CYCLING".equals(sportType);

        // ── file_id (global msg 0)
        defineMsg(0, 0, new int[][]{
                {4, 4, UINT32}, {1, 2, UINT16}, {2, 2, UINT16}, {3, 4, UINT32Z}, {0, 1, ENUM}
        });
        writeMsg(0, new int[]{endTs, 255, 0, 0, 4});

        // ── per-second records (global msg 20)
        List<? extends Number> timeStream = streams.get("time");
        List<? extends Number> powerStream = streams.get("watts");
        List<? extends Number> hrStream = streams.get("heartrate");
        List<? extends Number> cadenceStream = streams.get("cadence");
        List<? extends Number> velocityStream = streams.get("velocity_smooth");
        List<? extends Number> distanceStream = streams.get("distance");

        if (timeStream != null && !timeStream.isEmpty()) {
            if (cycling) {
                defineMsg(1, 20, new int[][]{
                        {253, 4, UINT32}, {7, 2, UINT16}, {3, 1, UINT8}, {4, 1, UINT8}, {6, 2, UINT16}
                });
            } else {
                defineMsg(1, 20, new int[][]{
                        {253, 4, UINT32}, {0, 4, UINT32}, {3, 1, UINT8}, {4, 1, UINT8}, {6, 2, UINT16}
                });
            }

            for (int i = 0; i < timeStream.size(); i++) {
                int ts = startTs + timeStream.get(i).intValue();
                int hr = intAt(hrStream, i);
                int cad = intAt(cadenceStream, i);
                int speedRaw = (int) Math.round(doubleAt(velocityStream, i) * 1000);

                if (cycling) {
                    int power = intAt(powerStream, i);
                    writeMsg(1, new int[]{ts, power, hr, cad, speedRaw});
                } else {
                    int distCm = (int) Math.round(doubleAt(distanceStream, i) * 100);
                    writeMsg(1, new int[]{ts, distCm, hr, cad, speedRaw});
                }
            }
        }

        // ── session (global msg 18)
        if (cycling) {
            defineMsg(2, 18, new int[][]{
                    {253, 4, UINT32}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32},
                    {0, 1, ENUM}, {1, 1, ENUM}, {5, 1, ENUM}, {6, 1, ENUM},
                    {19, 2, UINT16}, {16, 1, UINT8}, {18, 1, UINT8},
                    {25, 2, UINT16}, {26, 2, UINT16}
            });
            writeMsg(2, new int[]{
                    endTs, startTs, elapsedMs, elapsedMs,
                    9, 1, sport, 0,
                    (int) avgPower, (int) avgHR, (int) avgCadence,
                    0, 1
            });
        } else {
            int totalDistCm = (int) Math.round(totalDurationSeconds * avgSpeed * 100);
            defineMsg(2, 18, new int[][]{
                    {253, 4, UINT32}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32},
                    {0, 1, ENUM}, {1, 1, ENUM}, {5, 1, ENUM}, {6, 1, ENUM},
                    {9, 4, UINT32}, {16, 1, UINT8}, {18, 1, UINT8},
                    {25, 2, UINT16}, {26, 2, UINT16}
            });
            writeMsg(2, new int[]{
                    endTs, startTs, elapsedMs, elapsedMs,
                    9, 1, sport, 0,
                    totalDistCm, (int) avgHR, (int) avgCadence,
                    0, 1
            });
        }

        // ── activity (global msg 34)
        defineMsg(3, 34, new int[][]{
                {253, 4, UINT32}, {0, 4, UINT32}, {1, 2, UINT16}, {2, 1, ENUM}, {3, 1, ENUM}, {4, 1, ENUM}
        });
        writeMsg(3, new int[]{endTs, elapsedMs, 1, 0, 26, 1});

        return finish();
    }

    private void defineMsg(int localType, int globalMsgNum, int[][] fields) {
        while (defs.size() <= localType) defs.add(null);
        defs.set(localType, fields);

        bytes.write(0x40 | localType);
        bytes.write(0x00); // reserved
        bytes.write(0x00); // architecture = little-endian
        writeLE(globalMsgNum, 2);
        bytes.write(fields.length);
        for (int[] f : fields) {
            bytes.write(f[0]); // field def num
            bytes.write(f[1]); // size
            bytes.write(f[2]); // base type
        }
    }

    private void writeMsg(int localType, int[] values) {
        int[][] fields = defs.get(localType);
        bytes.write(localType);
        for (int i = 0; i < fields.length; i++) {
            writeLE(i < values.length ? values[i] : 0, fields[i][1]);
        }
    }

    private byte[] finish() {
        byte[] data = bytes.toByteArray();
        int dataSize = data.length;

        // 14-byte file header
        byte[] hdr = new byte[14];
        hdr[0] = 14;         // header size
        hdr[1] = 0x20;       // protocol version 2.0
        hdr[2] = (byte) 0xd0; hdr[3] = 0x07; // profile version 2000 LE
        hdr[4] = (byte) (dataSize & 0xff);
        hdr[5] = (byte) ((dataSize >> 8) & 0xff);
        hdr[6] = (byte) ((dataSize >> 16) & 0xff);
        hdr[7] = (byte) ((dataSize >> 24) & 0xff);
        hdr[8] = '.'; hdr[9] = 'F'; hdr[10] = 'I'; hdr[11] = 'T';

        int hdrCrc = 0;
        for (int i = 0; i < 12; i++) hdrCrc = fitCrc(hdrCrc, hdr[i] & 0xff);
        hdr[12] = (byte) (hdrCrc & 0xff);
        hdr[13] = (byte) ((hdrCrc >> 8) & 0xff);

        int dataCrc = 0;
        for (byte b : data) dataCrc = fitCrc(dataCrc, b & 0xff);

        byte[] result = new byte[14 + dataSize + 2];
        System.arraycopy(hdr, 0, result, 0, 14);
        System.arraycopy(data, 0, result, 14, dataSize);
        result[14 + dataSize] = (byte) (dataCrc & 0xff);
        result[14 + dataSize + 1] = (byte) ((dataCrc >> 8) & 0xff);
        return result;
    }

    private void writeLE(int value, int size) {
        for (int i = 0; i < size; i++) {
            bytes.write((value >> (i * 8)) & 0xff);
        }
    }

    private static int fitCrc(int crc, int b) {
        int tmp = CRC_TABLE[crc & 0xf];
        crc = ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[b & 0xf];
        tmp = CRC_TABLE[crc & 0xf];
        return ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[(b >> 4) & 0xf];
    }

    private static int toFitTs(long unixSeconds) {
        return (int) Math.max(0, unixSeconds - FIT_EPOCH_OFFSET);
    }

    private static int fitSport(String sport) {
        if ("RUNNING".equals(sport)) return 1;
        if ("SWIMMING".equals(sport)) return 5;
        return 2; // CYCLING
    }

    private static int intAt(List<? extends Number> list, int index) {
        if (list == null || index >= list.size()) return 0;
        Number val = list.get(index);
        return val != null ? val.intValue() : 0;
    }

    private static double doubleAt(List<? extends Number> list, int index) {
        if (list == null || index >= list.size()) return 0;
        Number val = list.get(index);
        return val != null ? val.doubleValue() : 0;
    }
}
