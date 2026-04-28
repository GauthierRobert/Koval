import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FitRecord} from '../../../../services/metrics.service';
import {formatTimeHMS} from '../../../shared/format/format.utils';

export interface DetectedEffort {
    index: number;
    startSecond: number;
    durationSeconds: number;
    avgPower: number;
    normalizedPower: number;
    peakPower: number;
    intensityFactor: number | null;
    avgHr: number | null;
    avgCadence: number | null;
}

const DEFAULT_THRESHOLD_PCT = 0.85;
const HYSTERESIS_PCT = 0.05; // exit threshold = enter * (1 - HYSTERESIS_PCT) → 0.80 of FTP
const MIN_DURATION_S = 30;
const MAX_GAP_S = 8; // merge close efforts

@Component({
    selector: 'app-detected-efforts-panel',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './detected-efforts-panel.component.html',
    styleUrl: './detected-efforts-panel.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetectedEffortsPanelComponent {
    @Input({required: true}) records: FitRecord[] = [];
    @Input({required: true}) sportType = '';
    @Input() ftp: number | null = null;

    get efforts(): DetectedEffort[] | null {
        return this.detect();
    }

    formatDuration(seconds: number): string {
        return formatTimeHMS(seconds);
    }

    private detect(): DetectedEffort[] | null {
        if (this.sportType !== 'CYCLING') return null;
        if (!this.ftp || this.ftp <= 0) return null;
        if (!this.records || this.records.length < MIN_DURATION_S) return null;
        if (!this.records.some(r => r.power > 0)) return null;

        const enterW = this.ftp * DEFAULT_THRESHOLD_PCT;
        const exitW = this.ftp * (DEFAULT_THRESHOLD_PCT - HYSTERESIS_PCT);

        const recs = this.records;
        const t0 = recs[0].timestamp;

        type Span = {startIdx: number; endIdx: number};
        const spans: Span[] = [];
        let inEffort = false;
        let startIdx = 0;
        let belowFor = 0;

        for (let i = 0; i < recs.length; i++) {
            const p = recs[i].power;
            if (!inEffort) {
                if (p > enterW) {
                    inEffort = true;
                    startIdx = i;
                    belowFor = 0;
                }
            } else {
                if (p < exitW) {
                    belowFor++;
                    if (belowFor >= 5) {
                        spans.push({startIdx, endIdx: i - belowFor});
                        inEffort = false;
                    }
                } else {
                    belowFor = 0;
                }
            }
        }
        if (inEffort) spans.push({startIdx, endIdx: recs.length - 1});

        // Merge close spans
        const merged: Span[] = [];
        for (const s of spans) {
            if (merged.length === 0) {
                merged.push(s);
                continue;
            }
            const last = merged[merged.length - 1];
            const gap = recs[s.startIdx].timestamp - recs[last.endIdx].timestamp;
            if (gap <= MAX_GAP_S) {
                last.endIdx = s.endIdx;
            } else {
                merged.push(s);
            }
        }

        const efforts: DetectedEffort[] = [];
        for (const span of merged) {
            const slice = recs.slice(span.startIdx, span.endIdx + 1);
            const duration = slice[slice.length - 1].timestamp - slice[0].timestamp + 1;
            if (duration < MIN_DURATION_S) continue;

            let sumP = 0;
            let peak = 0;
            const powers: number[] = [];
            let sumHr = 0, hrCount = 0;
            let sumCad = 0, cadCount = 0;
            for (const r of slice) {
                sumP += r.power;
                if (r.power > peak) peak = r.power;
                powers.push(r.power);
                if (r.heartRate > 0) { sumHr += r.heartRate; hrCount++; }
                if (r.cadence > 0) { sumCad += r.cadence; cadCount++; }
            }
            const avgPower = sumP / slice.length;
            const np = computeNormalizedPower(powers);
            const intensityFactor = this.ftp ? np / this.ftp : null;

            efforts.push({
                index: efforts.length + 1,
                startSecond: slice[0].timestamp - t0,
                durationSeconds: Math.round(duration),
                avgPower: Math.round(avgPower),
                normalizedPower: Math.round(np),
                peakPower: peak,
                intensityFactor: intensityFactor != null ? Math.round(intensityFactor * 100) / 100 : null,
                avgHr: hrCount > 0 ? Math.round(sumHr / hrCount) : null,
                avgCadence: cadCount > 0 ? Math.round(sumCad / cadCount) : null,
            });
        }
        return efforts;
    }
}

/**
 * Normalized Power: rolling 30-second average → fourth-power mean → fourth root.
 * For sub-30-second efforts, falls back to plain RMS-style calculation over
 * available samples — the standard NP formula needs ≥30 samples to be meaningful.
 */
function computeNormalizedPower(powers: number[]): number {
    if (powers.length === 0) return 0;
    const window = 30;
    if (powers.length < window) {
        let sum = 0;
        for (const p of powers) sum += Math.pow(p, 4);
        return Math.pow(sum / powers.length, 0.25);
    }
    const rolling: number[] = [];
    let sum = 0;
    for (let i = 0; i < window; i++) sum += powers[i];
    rolling.push(sum / window);
    for (let i = window; i < powers.length; i++) {
        sum += powers[i] - powers[i - window];
        rolling.push(sum / window);
    }
    let fourth = 0;
    for (const v of rolling) fourth += Math.pow(v, 4);
    return Math.pow(fourth / rolling.length, 0.25);
}
