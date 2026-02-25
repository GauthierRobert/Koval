import { Injectable } from '@angular/core';
import { SessionSummary } from './workout-execution.service';

// FIT epoch offset: seconds between Unix epoch and FIT epoch (1989-12-31T00:00:00Z)
const FIT_EPOCH_OFFSET = 631065600;

// FIT CRC-16 lookup table
const CRC_TABLE = [
    0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
    0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400,
];

function fitCrc(crc: number, byte: number): number {
    let tmp = CRC_TABLE[crc & 0xf];
    crc = ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[byte & 0xf];
    tmp = CRC_TABLE[crc & 0xf];
    return ((crc >> 4) & 0x0fff) ^ tmp ^ CRC_TABLE[(byte >> 4) & 0xf];
}

// Base type codes (size encoded in the field definition, base type sets validation rule)
const ENUM   = 0x00; // 1 byte
const UINT8  = 0x02; // 1 byte
const UINT16 = 0x84; // 2 bytes LE
const UINT32 = 0x86; // 4 bytes LE
const UINT32Z = 0x8c; // 4 bytes LE (zero-invalid sentinel)

type FieldDef = [defNum: number, size: number, baseType: number];

/**
 * Minimal FIT binary writer.
 * Handles definition messages and data messages in little-endian format,
 * then wraps the payload in a valid 14-byte file header with CRC-16.
 */
class FitWriter {
    private bytes: number[] = [];
    private defs = new Map<number, FieldDef[]>();

    defineMsg(localType: number, globalMsgNum: number, fields: FieldDef[]): void {
        this.defs.set(localType, fields);
        // Definition message header: 0x40 | localType
        this.bytes.push(0x40 | localType, 0x00, 0x00); // header, reserved, architecture=LE
        this.pushN(globalMsgNum, 2);
        this.bytes.push(fields.length);
        for (const [defNum, size, baseType] of fields) {
            this.bytes.push(defNum, size, baseType);
        }
    }

    writeMsg(localType: number, values: number[]): void {
        const fields = this.defs.get(localType)!;
        this.bytes.push(localType);
        fields.forEach(([, size], i) => this.pushN(values[i] ?? 0, size));
    }

    finish(): ArrayBuffer {
        const dataSize = this.bytes.length;

        // 14-byte file header
        const hdr = [
            14, 0x20,                               // header size, protocol version 2.0
            0xd0, 0x07,                             // profile version 2000 (LE)
            dataSize & 0xff, (dataSize >> 8) & 0xff,
            (dataSize >> 16) & 0xff, (dataSize >> 24) & 0xff,
            0x2e, 0x46, 0x49, 0x54,                 // ".FIT"
            0x00, 0x00,                             // header CRC placeholder
        ];

        let hdrCrc = 0;
        for (let i = 0; i < 12; i++) hdrCrc = fitCrc(hdrCrc, hdr[i]);
        hdr[12] = hdrCrc & 0xff;
        hdr[13] = (hdrCrc >> 8) & 0xff;

        let dataCrc = 0;
        for (const b of this.bytes) dataCrc = fitCrc(dataCrc, b);

        return new Uint8Array([
            ...hdr, ...this.bytes, dataCrc & 0xff, (dataCrc >> 8) & 0xff,
        ]).buffer;
    }

    private pushN(value: number, size: number): void {
        for (let i = 0; i < size; i++) this.bytes.push((value >> (i * 8)) & 0xff);
    }
}

@Injectable({ providedIn: 'root' })
export class FitExportService {

    /**
     * Generate and download a .fit file for the given session.
     * @param summary  SessionSummary (with optional history[] for per-second records)
     * @param date     Session end time (defaults to now)
     */
    exportSession(summary: SessionSummary, date: Date = new Date()): void {
        const buffer = this.buildFit(summary, date);
        const filename = this.sanitize(summary.title) + '.fit';
        const blob = new Blob([buffer], { type: 'application/octet-stream' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    buildFit(summary: SessionSummary, endDate: Date): ArrayBuffer {
        const w = new FitWriter();

        const endTs   = this.toFitTs(endDate.getTime() / 1000);
        const startTs = endTs - summary.totalDuration;
        const elapsed = summary.totalDuration * 1000; // FIT stores elapsed time in milliseconds
        const sport   = this.fitSport(summary.sportType);
        const numLaps = summary.blockSummaries?.length || 1;

        // ── file_id (global msg 0) ─────────────────────────────────────────────
        w.defineMsg(0, 0, [
            [4, 4, UINT32 ],  // time_created  (DATE_TIME = seconds since FIT epoch)
            [1, 2, UINT16 ],  // manufacturer  (255 = development / no manufacturer)
            [2, 2, UINT16 ],  // product
            [3, 4, UINT32Z],  // serial_number
            [0, 1, ENUM   ],  // type          (4 = activity)
        ]);
        w.writeMsg(0, [endTs, 255, 0, 0, 4]);

        const isCycling = summary.sportType === 'CYCLING';
        const isSwimming = summary.sportType === 'SWIMMING';
        const subSport = isSwimming ? 4 : 0; // 4 = lap_swimming, 0 = generic

        // ── laps (global msg 19) ───────────────────────────────────────────────
        if (summary.blockSummaries?.length) {
            if (isCycling) {
                w.defineMsg(1, 19, [
                    [253, 4, UINT32], // timestamp       (lap end)
                    [2,   4, UINT32], // start_time
                    [7,   4, UINT32], // total_elapsed_time (ms)
                    [8,   4, UINT32], // total_timer_time   (ms)
                    [0,   1, ENUM  ], // event          (9 = lap)
                    [1,   1, ENUM  ], // event_type     (1 = stop)
                    [5,   1, ENUM  ], // sport
                    [6,   1, ENUM  ], // sub_sport
                    [19,  2, UINT16], // avg_power (W)
                    [16,  1, UINT8 ], // avg_heart_rate (bpm)
                    [18,  1, UINT8 ], // avg_cadence (rpm)
                ]);
            } else {
                w.defineMsg(1, 19, [
                    [253, 4, UINT32], // timestamp
                    [2,   4, UINT32], // start_time
                    [7,   4, UINT32], // total_elapsed_time (ms)
                    [8,   4, UINT32], // total_timer_time   (ms)
                    [0,   1, ENUM  ], // event
                    [1,   1, ENUM  ], // event_type
                    [5,   1, ENUM  ], // sport
                    [6,   1, ENUM  ], // sub_sport
                    [9,   4, UINT32], // total_distance (cm)
                    [16,  1, UINT8 ], // avg_heart_rate (bpm)
                    [18,  1, UINT8 ], // avg_cadence (rpm/spm)
                ]);
            }

            let lapStartTs = startTs;
            let cumulativeDistanceCm = 0;
            for (const lap of summary.blockSummaries) {
                const lapEndTs = lapStartTs + lap.durationSeconds;
                const lapMs    = lap.durationSeconds * 1000;
                if (isCycling) {
                    w.writeMsg(1, [lapEndTs, lapStartTs, lapMs, lapMs, 9, 1, sport, subSport,
                                   lap.actualPower, lap.actualHR, lap.actualCadence]);
                } else {
                    // Estimate lap distance from average speed (stored in actualPower slot for non-cycling)
                    const lapDistanceCm = Math.round(lap.durationSeconds * (summary.avgSpeed || 0) * 100);
                    cumulativeDistanceCm += lapDistanceCm;
                    w.writeMsg(1, [lapEndTs, lapStartTs, lapMs, lapMs, 9, 1, sport, subSport,
                                   lapDistanceCm, lap.actualHR, lap.actualCadence]);
                }
                lapStartTs = lapEndTs;
            }
        }

        // ── per-second records (global msg 20) ────────────────────────────────
        if (summary.history?.length) {
            if (isCycling) {
                w.defineMsg(2, 20, [
                    [253, 4, UINT32], // timestamp
                    [7,   2, UINT16], // power (W)
                    [3,   1, UINT8 ], // heart_rate (bpm)
                    [4,   1, UINT8 ], // cadence (rpm)
                    [6,   2, UINT16], // speed (m/s × 1000)
                ]);
                summary.history.forEach((m, i) => {
                    const speedRaw = Math.round((m.speed || 0) * 1000);
                    w.writeMsg(2, [startTs + i, m.power || 0, m.heartRate || 0, m.cadence || 0, speedRaw]);
                });
            } else {
                // Running / Swimming: replace power with cumulative distance (cm)
                w.defineMsg(2, 20, [
                    [253, 4, UINT32], // timestamp
                    [0,   4, UINT32], // distance (cm, accumulated)
                    [3,   1, UINT8 ], // heart_rate (bpm)
                    [4,   1, UINT8 ], // cadence (rpm/spm)
                    [6,   2, UINT16], // speed (m/s × 1000)
                ]);
                let distCm = 0;
                summary.history.forEach((m, i) => {
                    distCm += Math.round((m.speed || 0) * 100); // 1 second × speed(m/s) × 100 = cm
                    const speedRaw = Math.round((m.speed || 0) * 1000);
                    w.writeMsg(2, [startTs + i, distCm, m.heartRate || 0, m.cadence || 0, speedRaw]);
                });
            }
        }

        // ── session (global msg 18) ────────────────────────────────────────────
        if (isCycling) {
            w.defineMsg(3, 18, [
                [253, 4, UINT32], // timestamp      (session end)
                [2,   4, UINT32], // start_time
                [7,   4, UINT32], // total_elapsed_time (ms)
                [8,   4, UINT32], // total_timer_time   (ms)
                [0,   1, ENUM  ], // event          (9 = session)
                [1,   1, ENUM  ], // event_type     (1 = stop)
                [5,   1, ENUM  ], // sport
                [6,   1, ENUM  ], // sub_sport
                [19,  2, UINT16], // avg_power (W)
                [16,  1, UINT8 ], // avg_heart_rate (bpm)
                [18,  1, UINT8 ], // avg_cadence (rpm)
                [25,  2, UINT16], // first_lap_index
                [26,  2, UINT16], // num_laps
            ]);
            w.writeMsg(3, [
                endTs, startTs, elapsed, elapsed,
                9, 1, sport, subSport,
                summary.avgPower   || 0,
                summary.avgHR      || 0,
                summary.avgCadence || 0,
                0, numLaps,
            ]);
        } else {
            const totalDistanceCm = Math.round(summary.totalDuration * (summary.avgSpeed || 0) * 100);
            w.defineMsg(3, 18, [
                [253, 4, UINT32], // timestamp
                [2,   4, UINT32], // start_time
                [7,   4, UINT32], // total_elapsed_time (ms)
                [8,   4, UINT32], // total_timer_time   (ms)
                [0,   1, ENUM  ], // event
                [1,   1, ENUM  ], // event_type
                [5,   1, ENUM  ], // sport
                [6,   1, ENUM  ], // sub_sport
                [9,   4, UINT32], // total_distance (cm)
                [16,  1, UINT8 ], // avg_heart_rate (bpm)
                [18,  1, UINT8 ], // avg_cadence (rpm/spm)
                [25,  2, UINT16], // first_lap_index
                [26,  2, UINT16], // num_laps
            ]);
            w.writeMsg(3, [
                endTs, startTs, elapsed, elapsed,
                9, 1, sport, subSport,
                totalDistanceCm,
                summary.avgHR      || 0,
                summary.avgCadence || 0,
                0, numLaps,
            ]);
        }

        // ── activity (global msg 34) ───────────────────────────────────────────
        w.defineMsg(4, 34, [
            [253, 4, UINT32], // timestamp
            [0,   4, UINT32], // total_timer_time (ms)
            [1,   2, UINT16], // num_sessions
            [2,   1, ENUM  ], // type        (0 = manual)
            [3,   1, ENUM  ], // event       (26 = activity)
            [4,   1, ENUM  ], // event_type  (1 = stop)
        ]);
        w.writeMsg(4, [endTs, elapsed, 1, 0, 26, 1]);

        return w.finish();
    }

    /** Convert Unix timestamp (seconds) to FIT timestamp */
    private toFitTs(unixSeconds: number): number {
        return Math.max(0, Math.round(unixSeconds - FIT_EPOCH_OFFSET));
    }

    /** Map app sport type to FIT sport enum value */
    private fitSport(sport: string): number {
        if (sport === 'RUNNING')  return 1;
        if (sport === 'SWIMMING') return 5;
        return 2; // CYCLING is the default
    }

    private sanitize(name: string): string {
        return (name || 'session').replace(/[^a-z0-9]/gi, '_').toLowerCase();
    }
}
