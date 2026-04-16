import {Component, ChangeDetectionStrategy, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FitRecord} from '../../../../services/metrics.service';

export interface DecouplingSegment {
    label: string;
    avgMetric: number;
    avgHR: number;
    efficiency: number;
}

export interface DecouplingResult {
    segments: DecouplingSegment[];
    decouplingPct: number;
    color: string;
    level: 'good' | 'moderate' | 'high';
    usePower: boolean;
}

const NINETY_MINUTES = 5400;
const MIN_RECORDS = 300; // 5 minutes minimum
const GREEN = 'var(--success-color)';
const AMBER = 'oklch(0.75 0.16 75)';
const RED = 'var(--danger-color)';

@Component({
    selector: 'app-decoupling-gauge',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './decoupling-gauge.component.html',
    styleUrl: './decoupling-gauge.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecouplingGaugeComponent {
    @Input({required: true}) records: FitRecord[] = [];
    @Input({required: true}) sportType: string = '';

    get result(): DecouplingResult | null {
        return this.computeDecoupling();
    }

    /** SVG arc path for a segment of the semi-circle gauge. */
    arcPath(startPct: number, endPct: number, radius: number, cx: number, cy: number): string {
        // Map percentage (0-1) to angle (PI to 0, left-to-right semi-circle)
        const startAngle = Math.PI * (1 - startPct);
        const endAngle = Math.PI * (1 - endPct);
        const x1 = cx + radius * Math.cos(startAngle);
        const y1 = cy - radius * Math.sin(startAngle);
        const x2 = cx + radius * Math.cos(endAngle);
        const y2 = cy - radius * Math.sin(endAngle);
        const largeArc = Math.abs(endPct - startPct) > 0.5 ? 1 : 0;
        return `M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}`;
    }

    /** Position of the needle marker on the arc for a given decoupling %. */
    needlePos(decouplingPct: number, radius: number, cx: number, cy: number): {x: number; y: number} {
        // Scale: 0% maps to left (PI), 15%+ maps to right (0)
        const clamped = Math.max(0, Math.min(decouplingPct, 15));
        const pct = clamped / 15;
        const angle = Math.PI * (1 - pct);
        return {
            x: cx + radius * Math.cos(angle),
            y: cy - radius * Math.sin(angle),
        };
    }

    private computeDecoupling(): DecouplingResult | null {
        if (this.sportType === 'SWIMMING') return null;

        // Prefer power only if the session actually contains power data (cycling w/
        // a power meter). Otherwise fall back to speed — this makes the gauge work
        // for running and for power-less cycling sessions.
        const hasPower =
            this.sportType === 'CYCLING' &&
            this.records.some((r) => r.power > 0);
        const usePower = hasPower;

        const filtered = this.records.filter((r) => {
            if (r.heartRate <= 0) return false;
            return usePower ? r.power > 0 : r.speed > 0;
        });

        if (filtered.length < MIN_RECORDS) return null;

        const totalDuration =
            filtered.length >= 2
                ? filtered[filtered.length - 1].timestamp - filtered[0].timestamp
                : 0;
        const segmentCount = totalDuration > NINETY_MINUTES ? 3 : 2;
        const segmentSize = Math.floor(filtered.length / segmentCount);

        if (segmentSize < MIN_RECORDS / 2) return null;

        const segments: DecouplingSegment[] = [];
        const rawEfficiencies: number[] = [];
        const segmentLabels =
            segmentCount === 2
                ? ['1st half', '2nd half']
                : ['1st third', '2nd third', '3rd third'];

        for (let i = 0; i < segmentCount; i++) {
            const start = i * segmentSize;
            const end = i === segmentCount - 1 ? filtered.length : (i + 1) * segmentSize;
            const slice = filtered.slice(start, end);

            let metricSum = 0;
            let hrSum = 0;
            for (const r of slice) {
                metricSum += usePower ? r.power : r.speed;
                hrSum += r.heartRate;
            }
            const avgMetric = metricSum / slice.length;
            const avgHR = hrSum / slice.length;
            // Raw efficiency kept unrounded for decoupling % computation — for
            // running, speed (m/s) / HR is ~0.02, so rounding to 2 decimals
            // collapses segments to identical values and masks the drift.
            const rawEfficiency = avgHR > 0 ? avgMetric / avgHR : 0;
            rawEfficiencies.push(rawEfficiency);

            // Display values: power as W, speed as km/h. Efficiency display
            // precision scales with the metric's magnitude.
            const displayMetric = usePower ? avgMetric : avgMetric * 3.6;
            const metricDecimals = usePower ? 0 : 1;
            const efficiencyDecimals = usePower ? 2 : 4;
            const pow = (n: number) => Math.pow(10, n);

            segments.push({
                label: segmentLabels[i],
                avgMetric: Math.round(displayMetric * pow(metricDecimals)) / pow(metricDecimals),
                avgHR: Math.round(avgHR),
                efficiency: Math.round(rawEfficiency * pow(efficiencyDecimals)) / pow(efficiencyDecimals),
            });
        }

        const first = rawEfficiencies[0];
        const last = rawEfficiencies[rawEfficiencies.length - 1];
        const decouplingPct = first > 0 ? ((first - last) / first) * 100 : 0;
        const rounded = Math.round(decouplingPct * 10) / 10;
        const abs = Math.abs(rounded);

        let color: string;
        let level: 'good' | 'moderate' | 'high';
        if (abs < 5) {
            color = GREEN;
            level = 'good';
        } else if (abs < 10) {
            color = AMBER;
            level = 'moderate';
        } else {
            color = RED;
            level = 'high';
        }

        return {segments, decouplingPct: rounded, color, level, usePower};
    }
}
