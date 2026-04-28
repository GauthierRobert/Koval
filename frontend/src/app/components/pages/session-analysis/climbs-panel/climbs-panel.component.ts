import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FitRecord} from '../../../../services/metrics.service';
import {formatTimeHMS} from '../../../shared/format/format.utils';

export interface DetectedClimb {
    index: number;
    startSecond: number;
    durationSeconds: number;
    distanceMeters: number;
    elevationGain: number;
    avgGrade: number;
    vam: number;
    avgPower: number | null;
    avgHr: number | null;
    category: 'HC' | '1' | '2' | '3' | '4';
}

const MIN_DURATION_S = 120;
const MIN_GAIN_M = 30;
const MIN_AVG_GRADE = 0.03; // 3%
const SMOOTH_WINDOW = 15; // seconds, for elevation noise
const DESCENT_END_M = -5; // end climb when sustained downtrend

@Component({
    selector: 'app-climbs-panel',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './climbs-panel.component.html',
    styleUrl: './climbs-panel.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClimbsPanelComponent {
    @Input({required: true}) records: FitRecord[] = [];
    @Input({required: true}) sportType = '';

    get climbs(): DetectedClimb[] | null {
        return this.detect();
    }

    formatDuration(seconds: number): string {
        return formatTimeHMS(seconds);
    }

    formatDistance(m: number): string {
        if (m >= 1000) return (m / 1000).toFixed(2) + ' km';
        return Math.round(m) + ' m';
    }

    private detect(): DetectedClimb[] | null {
        if (!this.records || this.records.length < MIN_DURATION_S) return null;
        if (!this.records.some(r => r.elevation != null)) return null;
        if (this.sportType === 'SWIMMING') return null;

        const recs = this.records;
        const t0 = recs[0].timestamp;

        // Smooth elevation with a simple centered moving average to suppress GPS jitter
        const elev: number[] = new Array(recs.length);
        for (let i = 0; i < recs.length; i++) {
            const lo = Math.max(0, i - SMOOTH_WINDOW);
            const hi = Math.min(recs.length - 1, i + SMOOTH_WINDOW);
            let sum = 0, count = 0;
            for (let j = lo; j <= hi; j++) {
                const e = recs[j].elevation;
                if (e != null) { sum += e; count++; }
            }
            elev[i] = count > 0 ? sum / count : 0;
        }

        type Span = {startIdx: number; endIdx: number};
        const spans: Span[] = [];
        let inClimb = false;
        let startIdx = 0;
        let peakElev = 0;

        for (let i = 1; i < recs.length; i++) {
            const delta = elev[i] - elev[i - 1];
            if (!inClimb) {
                if (delta > 0) {
                    inClimb = true;
                    startIdx = i - 1;
                    peakElev = elev[i];
                }
            } else {
                if (elev[i] > peakElev) peakElev = elev[i];
                // End when we've descended significantly from the peak
                if (elev[i] < peakElev + DESCENT_END_M) {
                    spans.push({startIdx, endIdx: i});
                    inClimb = false;
                }
            }
        }
        if (inClimb) spans.push({startIdx, endIdx: recs.length - 1});

        const climbs: DetectedClimb[] = [];
        for (const span of spans) {
            const slice = recs.slice(span.startIdx, span.endIdx + 1);
            if (slice.length < MIN_DURATION_S) continue;

            const elevStart = elev[span.startIdx];
            const elevEnd = elev[span.endIdx];
            const gain = elevEnd - elevStart;
            if (gain < MIN_GAIN_M) continue;

            const distance = (slice[slice.length - 1].distance || 0) - (slice[0].distance || 0);
            if (distance <= 0) continue;

            const avgGrade = gain / distance;
            if (avgGrade < MIN_AVG_GRADE) continue;

            const duration = slice[slice.length - 1].timestamp - slice[0].timestamp + 1;
            const vam = (gain / duration) * 3600;

            let sumP = 0, pCount = 0, sumHr = 0, hrCount = 0;
            for (const r of slice) {
                if (r.power > 0) { sumP += r.power; pCount++; }
                if (r.heartRate > 0) { sumHr += r.heartRate; hrCount++; }
            }

            climbs.push({
                index: climbs.length + 1,
                startSecond: slice[0].timestamp - t0,
                durationSeconds: Math.round(duration),
                distanceMeters: Math.round(distance),
                elevationGain: Math.round(gain),
                avgGrade: Math.round(avgGrade * 1000) / 10, // percent with 1 decimal
                vam: Math.round(vam),
                avgPower: pCount > 0 ? Math.round(sumP / pCount) : null,
                avgHr: hrCount > 0 ? Math.round(sumHr / hrCount) : null,
                category: this.categorize(gain, distance, avgGrade),
            });
        }
        return climbs;
    }

    /**
     * Approximate UCI-style climb categorization based on grade × length (FIETS-like).
     * Heuristic — for short climbs we lean on grade; for long ones we lean on gain.
     */
    private categorize(gainM: number, distanceM: number, gradePct: number): DetectedClimb['category'] {
        const score = (gainM * gainM) / (distanceM / 10);
        if (score >= 80 || gainM >= 800) return 'HC';
        if (score >= 40 || gainM >= 500) return '1';
        if (score >= 20 || gainM >= 300) return '2';
        if (score >= 8 || gainM >= 150) return '3';
        return '4';
    }
}
