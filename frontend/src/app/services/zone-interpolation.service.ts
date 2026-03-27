import {inject, Injectable} from '@angular/core';
import {Zone, ZoneBlock, SportType} from './zone';
import {FitRecord} from './metrics.service';
import {ZoneClassificationService} from './zone-classification.service';

@Injectable({providedIn: 'root'})
export class ZoneInterpolationService {

    private readonly cls = inject(ZoneClassificationService);
    private readonly MAX_MERGE_ZONE_GAP = 2;

    // ── Public API ───────────────────────────────────────────────────────

    computeZoneBlocks(
        records: FitRecord[], zones: Zone[], referenceValue: number, sport: SportType, smoothFactor: number,
    ): ZoneBlock[] {
        const n = records.length;
        if (n === 0) return [];

        // Pass 1 — raw classification (walking-aware for running)
        const rawZones = new Array<number>(n);
        const percents = new Array<number>(n);
        for (let i = 0; i < n; i++) {
            rawZones[i] = this.cls.classifyRecord(records[i].power, records[i].speed, sport, referenceValue, zones);
            percents[i] = this.cls.recordPercent(records[i].power, records[i].speed, sport, referenceValue);
        }

        // Pass 2 — median filter (window = 2 * smoothFactor + 1)
        const smoothed = this.medianFilter(rawZones, smoothFactor);

        // Pass 3 — group consecutive same-zone runs
        const minBlockSec = Math.max(5, smoothFactor * 2);
        let blocks = this.groupZoneRuns(smoothed, records, percents, zones);

        // Pass 4 — merge short blocks (bidirectional)
        blocks = this.mergeShortBlocks(blocks, minBlockSec, records, percents, zones);

        // Pass 5 — reclassify by average percent (skip walking blocks)
        this.reclassifyByAvgPercent(blocks, zones);

        // Pass 6 — collapse adjacent same-zone blocks
        blocks = this.collapseAdjacentSameZone(blocks, records, percents);

        // Pass 7 — fix durations to be contiguous
        for (let i = 0; i < blocks.length - 1; i++) {
            blocks[i].durationSeconds = records[blocks[i + 1].startIndex].timestamp - records[blocks[i].startIndex].timestamp;
        }

        // Pass 8 — compute distance for each block
        for (const b of blocks) {
            b.distanceMeters = this.computeBlockDistance(records, b.startIndex, b.endIndex);
        }

        return blocks;
    }

    // ── Pass 2: Median filter ────────────────────────────────────────────

    private medianFilter(raw: number[], smoothFactor: number): number[] {
        const n = raw.length;
        const half = smoothFactor;
        const walkIdx = this.cls.WALKING_ZONE_INDEX;
        const smoothed = new Array<number>(n);
        const buf: number[] = [];
        for (let i = 0; i < n; i++) {
            // Never let the median filter change a walking record to non-walking or vice versa
            if (raw[i] === walkIdx) {
                smoothed[i] = walkIdx;
                continue;
            }
            const lo = Math.max(0, i - half);
            const hi = Math.min(n - 1, i + half);
            buf.length = 0;
            for (let j = lo; j <= hi; j++) {
                if (raw[j] !== walkIdx) buf.push(raw[j]);
            }
            if (buf.length === 0) {
                smoothed[i] = raw[i];
                continue;
            }
            buf.sort((a, b) => a - b);
            smoothed[i] = buf[buf.length >> 1];
        }
        return smoothed;
    }

    // ── Pass 3: Group consecutive same-zone runs ─────────────────────────

    private groupZoneRuns(smoothed: number[], records: FitRecord[], percents: number[], zones: Zone[]): ZoneBlock[] {
        const blocks: ZoneBlock[] = [];
        const n = smoothed.length;
        let start = 0;
        for (let i = 1; i <= n; i++) {
            if (i < n && smoothed[i] === smoothed[start]) continue;
            blocks.push(this.buildBlock(smoothed[start], start, i - 1, records, percents, zones));
            start = i;
        }
        return blocks;
    }

    // ── Pass 4: Merge short blocks (bidirectional) ───────────────────────

    private mergeShortBlocks(
        blocks: ZoneBlock[], minBlockSec: number,
        records: FitRecord[], percents: number[], zones: Zone[],
    ): ZoneBlock[] {
        // Forward pass
        blocks = this.mergeShortBlocksSinglePass(blocks, minBlockSec, records, percents, zones);
        // Reverse pass — eliminates forward-only directional bias
        blocks.reverse();
        blocks = this.mergeShortBlocksSinglePass(blocks, minBlockSec, records, percents, zones);
        blocks.reverse();
        return blocks;
    }

    private mergeShortBlocksSinglePass(
        blocks: ZoneBlock[], minBlockSec: number,
        records: FitRecord[], percents: number[], zones: Zone[],
    ): ZoneBlock[] {
        let changed = true;
        while (changed) {
            changed = false;
            const next: ZoneBlock[] = [];
            for (let i = 0; i < blocks.length; i++) {
                const b = blocks[i];
                if (b.durationSeconds < minBlockSec && blocks.length > 1) {
                    const prev = next.length > 0 ? next[next.length - 1] : null;
                    const nxt = i + 1 < blocks.length ? blocks[i + 1] : null;

                    const prevDist = prev ? this.cls.zoneDistance(prev.zoneIndex, b.zoneIndex) : Infinity;
                    const nxtDist = nxt ? this.cls.zoneDistance(nxt.zoneIndex, b.zoneIndex) : Infinity;
                    const prevOk = prev !== null && prevDist <= this.MAX_MERGE_ZONE_GAP;
                    const nxtOk = nxt !== null && nxtDist <= this.MAX_MERGE_ZONE_GAP;

                    if (!prevOk && !nxtOk) {
                        next.push(b);
                        continue;
                    }

                    let target: ZoneBlock | null;
                    if (prevOk && nxtOk) {
                        if (prevDist < nxtDist) target = prev;
                        else if (nxtDist < prevDist) target = nxt;
                        else target = prev!.durationSeconds >= nxt!.durationSeconds ? prev : nxt;
                    } else {
                        target = prevOk ? prev : nxt;
                    }

                    if (target === prev) {
                        this.mergeBlockInto(prev!, b, records, percents);
                        changed = true;
                        continue;
                    } else if (target === nxt) {
                        this.mergeBlockInto(nxt!, b, records, percents);
                        changed = true;
                        next.push(nxt!);
                        i++;
                        continue;
                    }
                }
                next.push(b);
            }
            blocks = next;
            blocks = this.collapseAdjacentSameZone(blocks, records, percents);
        }
        return blocks;
    }

    // ── Pass 5: Reclassify by average percent ────────────────────────────

    private reclassifyByAvgPercent(blocks: ZoneBlock[], zones: Zone[]): void {
        for (const b of blocks) {
            if (b.zoneIndex === this.cls.WALKING_ZONE_INDEX) continue;
            const newZi = this.cls.classifyZone(b.avgPercent, zones);
            if (newZi !== b.zoneIndex) {
                b.zoneIndex = newZi;
                b.zoneLabel = this.cls.getZoneLabel(newZi, zones);
                b.zoneDescription = this.cls.getZoneDescription(newZi, zones);
                b.color = this.cls.getZoneColor(newZi);
            }
        }
    }

    // ── Pass 6: Collapse adjacent same-zone ──────────────────────────────

    private collapseAdjacentSameZone(blocks: ZoneBlock[], records: FitRecord[], percents: number[]): ZoneBlock[] {
        if (blocks.length < 2) return blocks;
        const out: ZoneBlock[] = [blocks[0]];
        for (let i = 1; i < blocks.length; i++) {
            const prev = out[out.length - 1];
            if (blocks[i].zoneIndex === prev.zoneIndex) {
                this.mergeBlockInto(prev, blocks[i], records, percents);
            } else {
                out.push(blocks[i]);
            }
        }
        return out;
    }

    // ── Block building & merging ─────────────────────────────────────────

    private buildBlock(zi: number, start: number, end: number, records: FitRecord[], percents: number[], zones: Zone[]): ZoneBlock {
        let sumPower = 0, sumSpeed = 0, sumHR = 0, sumCad = 0, sumPct = 0;
        let maxPower = 0, maxSpeed = 0;
        for (let j = start; j <= end; j++) {
            sumPower += records[j].power;
            sumSpeed += records[j].speed;
            sumHR += records[j].heartRate;
            sumCad += records[j].cadence;
            sumPct += percents[j];
            if (records[j].power > maxPower) maxPower = records[j].power;
            if (records[j].speed > maxSpeed) maxSpeed = records[j].speed;
        }
        const count = end - start + 1;
        return {
            zoneIndex: zi,
            zoneLabel: this.cls.getZoneLabel(zi, zones),
            zoneDescription: this.cls.getZoneDescription(zi, zones),
            color: this.cls.getZoneColor(zi),
            startIndex: start,
            endIndex: end,
            durationSeconds: records[end].timestamp - records[start].timestamp,
            distanceMeters: 0,
            avgPower: Math.round(sumPower / count),
            maxPower: Math.round(maxPower),
            avgSpeed: Math.round((sumSpeed / count) * 36) / 10,
            maxSpeed: Math.round(maxSpeed * 36) / 10,
            avgHR: Math.round(sumHR / count),
            avgCadence: Math.round(sumCad / count),
            avgPercent: Math.round(sumPct / count),
        };
    }

    private mergeBlockInto(target: ZoneBlock, source: ZoneBlock, records: FitRecord[], percents: number[]): void {
        const newStart = Math.min(target.startIndex, source.startIndex);
        const newEnd = Math.max(target.endIndex, source.endIndex);
        const count = newEnd - newStart + 1;
        let sumPower = 0, sumSpeed = 0, sumHR = 0, sumCad = 0, sumPct = 0;
        let maxPower = 0, maxSpeed = 0;
        for (let j = newStart; j <= newEnd; j++) {
            sumPower += records[j].power;
            sumSpeed += records[j].speed;
            sumHR += records[j].heartRate;
            sumCad += records[j].cadence;
            sumPct += percents[j];
            if (records[j].power > maxPower) maxPower = records[j].power;
            if (records[j].speed > maxSpeed) maxSpeed = records[j].speed;
        }
        target.avgPower = Math.round(sumPower / count);
        target.maxPower = Math.round(maxPower);
        target.avgSpeed = Math.round((sumSpeed / count) * 36) / 10;
        target.maxSpeed = Math.round(maxSpeed * 36) / 10;
        target.avgHR = Math.round(sumHR / count);
        target.avgCadence = Math.round(sumCad / count);
        target.avgPercent = Math.round(sumPct / count);
        target.startIndex = newStart;
        target.endIndex = newEnd;
        target.durationSeconds = records[newEnd].timestamp - records[newStart].timestamp;
    }

    // ── Distance computation ─────────────────────────────────────────────

    private computeBlockDistance(records: FitRecord[], start: number, end: number): number {
        if (start >= end) return 0;
        // Use cumulative distance from FIT if available
        if (records[end].distance > 0 && records[start].distance >= 0) {
            return Math.round(records[end].distance - records[start].distance);
        }
        // Fall back to trapezoidal integration of speed
        let distance = 0;
        for (let i = start + 1; i <= end; i++) {
            const dt = records[i].timestamp - records[i - 1].timestamp;
            distance += ((records[i].speed + records[i - 1].speed) / 2) * dt;
        }
        return Math.round(distance);
    }
}
