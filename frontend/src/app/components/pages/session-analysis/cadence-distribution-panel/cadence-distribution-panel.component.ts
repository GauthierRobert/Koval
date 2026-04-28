import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FitRecord} from '../../../../services/metrics.service';

export interface CadenceBucket {
    label: string;
    minRpm: number;
    maxRpm: number;
    seconds: number;
    percentage: number;
    color: string;
}

export type CadenceProfile = 'grinder' | 'balanced' | 'spinner' | 'mixed';

export interface CadenceProfileResult {
    buckets: CadenceBucket[];
    medianRpm: number;
    grinderPct: number;
    optimalPct: number;
    highPct: number;
    spinningPct: number;
    profile: CadenceProfile;
    totalSeconds: number;
}

const BUCKETS: {label: string; min: number; max: number; color: string}[] = [
    {label: '<60', min: 0, max: 60, color: 'oklch(0.55 0.16 25)'},
    {label: '60–70', min: 60, max: 70, color: 'oklch(0.65 0.14 45)'},
    {label: '70–80', min: 70, max: 80, color: 'oklch(0.72 0.12 75)'},
    {label: '80–90', min: 80, max: 90, color: 'oklch(0.78 0.14 130)'},
    {label: '90–100', min: 90, max: 100, color: 'oklch(0.78 0.16 155)'},
    {label: '100–110', min: 100, max: 110, color: 'oklch(0.72 0.16 200)'},
    {label: '110+', min: 110, max: Infinity, color: 'oklch(0.65 0.18 240)'},
];

@Component({
    selector: 'app-cadence-distribution-panel',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './cadence-distribution-panel.component.html',
    styleUrl: './cadence-distribution-panel.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CadenceDistributionPanelComponent {
    @Input({required: true}) records: FitRecord[] = [];
    @Input({required: true}) sportType = '';

    get result(): CadenceProfileResult | null {
        return this.computeProfile();
    }

    formatDuration(seconds: number): string {
        if (seconds >= 3600) {
            const h = Math.floor(seconds / 3600);
            const m = Math.floor((seconds % 3600) / 60);
            return `${h}h ${m}m`;
        }
        if (seconds >= 60) {
            const m = Math.floor(seconds / 60);
            const s = seconds % 60;
            return `${m}m ${s}s`;
        }
        return `${seconds}s`;
    }

    private computeProfile(): CadenceProfileResult | null {
        if (this.sportType !== 'CYCLING') return null;
        if (!this.records || this.records.length < 30) return null;

        const seconds = BUCKETS.map(() => 0);
        const cadences: number[] = [];
        let total = 0;

        for (let i = 0; i < this.records.length; i++) {
            const r = this.records[i];
            if (r.cadence <= 0) continue;
            const dt = i + 1 < this.records.length
                ? Math.min(this.records[i + 1].timestamp - r.timestamp, 30)
                : 1;
            if (dt <= 0) continue;
            const idx = this.bucketIndex(r.cadence);
            seconds[idx] += dt;
            cadences.push(r.cadence);
            total += dt;
        }

        if (total < 30 || cadences.length === 0) return null;

        const buckets: CadenceBucket[] = BUCKETS.map((b, i) => ({
            label: b.label,
            minRpm: b.min,
            maxRpm: b.max === Infinity ? 999 : b.max,
            seconds: Math.round(seconds[i]),
            percentage: Math.round((seconds[i] / total) * 100),
            color: b.color,
        }));

        cadences.sort((a, b) => a - b);
        const median = cadences[Math.floor(cadences.length / 2)];

        const grinderSec = seconds[0] + seconds[1]; // <70
        const optimalSec = seconds[2] + seconds[3] + seconds[4]; // 70-100
        const highSec = seconds[5]; // 100-110
        const spinningSec = seconds[6]; // 110+

        const grinderPct = Math.round((grinderSec / total) * 100);
        const optimalPct = Math.round((optimalSec / total) * 100);
        const highPct = Math.round((highSec / total) * 100);
        const spinningPct = Math.round((spinningSec / total) * 100);

        const profile = this.classifyProfile(grinderPct, optimalPct, highPct, spinningPct);

        return {
            buckets,
            medianRpm: median,
            grinderPct,
            optimalPct,
            highPct,
            spinningPct,
            profile,
            totalSeconds: total,
        };
    }

    private bucketIndex(rpm: number): number {
        for (let i = 0; i < BUCKETS.length; i++) {
            if (rpm >= BUCKETS[i].min && rpm < BUCKETS[i].max) return i;
        }
        return BUCKETS.length - 1;
    }

    private classifyProfile(grinder: number, optimal: number, high: number, spinning: number): CadenceProfile {
        if (grinder >= 40) return 'grinder';
        if (spinning >= 30 || (high + spinning) >= 50) return 'spinner';
        if (optimal >= 60) return 'balanced';
        return 'mixed';
    }
}
