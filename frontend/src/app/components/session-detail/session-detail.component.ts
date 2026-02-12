import {
    Component,
    Input,
    ViewChild,
    ElementRef,
    AfterViewInit,
    OnChanges,
    SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { SavedSession } from '../../services/history.service';
import { BlockSummary, LiveMetrics } from '../../services/workout-execution.service';

@Component({
    selector: 'app-session-detail',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './session-detail.component.html',
    styleUrls: ['./session-detail.component.css'],
})
export class SessionDetailComponent implements AfterViewInit, OnChanges {
    @Input() session!: SavedSession;

    @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

    showPower = true;
    showHR = true;
    showCadence = true;

    private ctx!: CanvasRenderingContext2D;
    private drawn = false;

    get hasHistory(): boolean {
        return !!this.session.history && this.session.history.length > 0;
    }

    ngAfterViewInit() {
        if (this.hasHistory) {
            setTimeout(() => {
                this.initCanvas();
                this.drawChart();
            });
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes['session'] && this.drawn && this.hasHistory) {
            this.ctx = undefined!;
            setTimeout(() => {
                this.initCanvas();
                this.drawChart();
            });
        }
    }

    private initCanvas() {
        const canvas = this.chartCanvas.nativeElement;
        const parent = canvas.parentElement!;
        canvas.width = parent.clientWidth;
        canvas.height = parent.clientHeight;
        this.ctx = canvas.getContext('2d')!;
    }

    drawChart() {
        if (!this.chartCanvas || !this.hasHistory) return;

        if (!this.ctx) {
            this.initCanvas();
        }

        const canvas = this.chartCanvas.nativeElement;
        const ctx = this.ctx;
        const history = this.session.history;

        const padding = { left: 50, right: 20, top: 16, bottom: 28 };
        const chartWidth = canvas.width - padding.left - padding.right;
        const chartHeight = canvas.height - padding.top - padding.bottom;

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Compute max values for scaling
        let maxPower = 0;
        let maxHR = 0;
        let maxCadence = 0;
        for (const m of history) {
            if (m.power > maxPower) maxPower = m.power;
            if (m.heartRate && m.heartRate > maxHR) maxHR = m.heartRate;
            if (m.cadence > maxCadence) maxCadence = m.cadence;
        }
        // Add 10% headroom and round up to nice numbers
        maxPower = Math.ceil((maxPower * 1.1) / 50) * 50 || 400;
        maxHR = Math.ceil((maxHR * 1.1) / 10) * 10 || 200;
        maxCadence = Math.ceil((maxCadence * 1.1) / 10) * 10 || 120;

        // Pick the primary Y-axis based on which metrics are shown
        let primaryMax = maxPower;
        let primaryUnit = 'W';
        if (this.showPower) {
            primaryMax = maxPower;
            primaryUnit = 'W';
        } else if (this.showHR) {
            primaryMax = maxHR;
            primaryUnit = 'bpm';
        } else if (this.showCadence) {
            primaryMax = maxCadence;
            primaryUnit = 'rpm';
        }

        // Draw grid
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
        ctx.fillStyle = 'rgba(255, 255, 255, 0.35)';
        ctx.font = '10px Inter, system-ui, sans-serif';
        ctx.textAlign = 'right';
        ctx.lineWidth = 1;

        const ySteps = 4;
        for (let i = 0; i <= ySteps; i++) {
            const val = (primaryMax / ySteps) * i;
            const y = padding.top + chartHeight - (val / primaryMax) * chartHeight;

            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(padding.left + chartWidth, y);
            ctx.stroke();

            ctx.fillText(`${Math.round(val)}${primaryUnit}`, padding.left - 8, y + 4);
        }

        // Draw X-axis time labels
        ctx.textAlign = 'center';
        ctx.fillStyle = 'rgba(255, 255, 255, 0.35)';
        const totalSeconds = this.session.totalDuration;
        const xLabelCount = Math.min(6, Math.max(2, Math.floor(totalSeconds / 60)));
        for (let i = 0; i <= xLabelCount; i++) {
            const t = Math.round((totalSeconds / xLabelCount) * i);
            const x = padding.left + (t / totalSeconds) * chartWidth;
            const min = Math.floor(t / 60);
            const sec = t % 60;
            ctx.fillText(`${min}:${sec.toString().padStart(2, '0')}`, x, padding.top + chartHeight + 18);
        }

        // Draw block boundaries as vertical dashed lines
        if (this.session.blockSummaries.length > 1) {
            ctx.save();
            ctx.setLineDash([4, 4]);
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
            ctx.lineWidth = 1;

            let accTime = 0;
            for (let i = 0; i < this.session.blockSummaries.length - 1; i++) {
                accTime += this.session.blockSummaries[i].durationSeconds;
                const x = padding.left + (accTime / totalSeconds) * chartWidth;
                ctx.beginPath();
                ctx.moveTo(x, padding.top);
                ctx.lineTo(x, padding.top + chartHeight);
                ctx.stroke();
            }
            ctx.restore();
        }

        const step = chartWidth / Math.max(history.length - 1, 1);

        // Helper to draw a metric line with optional fill
        const drawLine = (
            getValue: (m: LiveMetrics) => number,
            maxVal: number,
            color: string,
            fill: boolean,
            dashed: boolean
        ) => {
            ctx.save();
            ctx.beginPath();
            if (dashed) ctx.setLineDash([6, 4]);
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.5;

            let firstPoint = true;
            for (let i = 0; i < history.length; i++) {
                const val = getValue(history[i]);
                if (val === undefined || val === null) continue;
                const x = padding.left + i * step;
                const normalized = Math.min(val, maxVal);
                const y = padding.top + chartHeight - (normalized / maxVal) * chartHeight;

                if (firstPoint) {
                    ctx.moveTo(x, y);
                    firstPoint = false;
                } else {
                    ctx.lineTo(x, y);
                }
            }
            ctx.stroke();

            if (fill && !firstPoint) {
                // Close the path for fill
                const lastX = padding.left + (history.length - 1) * step;
                ctx.lineTo(lastX, padding.top + chartHeight);
                ctx.lineTo(padding.left, padding.top + chartHeight);
                ctx.closePath();
                ctx.fillStyle = color.replace('1)', '0.08)').replace('rgb', 'rgba');
                ctx.fill();
            }

            ctx.restore();
        };

        // Draw metrics (order: cadence behind, HR middle, power front)
        // Each metric is drawn against its own max so all lines fill the chart height
        if (this.showCadence) {
            drawLine(m => m.cadence, maxCadence, 'rgba(52, 152, 219, 1)', false, true);
        }

        if (this.showHR) {
            drawLine(m => m.heartRate ?? 0, maxHR, 'rgba(231, 76, 60, 1)', false, false);
        }

        if (this.showPower) {
            drawLine(m => m.power, maxPower, 'rgba(243, 156, 18, 1)', true, false);
        }

        this.drawn = true;
    }

    formatTime(seconds: number): string {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = seconds % 60;
        if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    formatDate(date: Date): string {
        return new Date(date).toLocaleDateString('en-US', {
            weekday: 'long',
            month: 'long',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    }

    getDelta(block: BlockSummary): number {
        if (block.targetPower === 0) return 0;
        const diff = block.actualPower - block.targetPower;
        return Math.round((diff / block.targetPower) * 100);
    }
}
