import {
    Component,
    Input,
    OnChanges,
    AfterViewInit,
    ViewChild,
    ElementRef,
} from '@angular/core';
import { ScheduledWorkout } from '../../services/coach.service';

interface DayInfo {
    date: Date;
    key: string;
    isToday: boolean;
}

@Component({
    selector: 'app-training-load-chart',
    standalone: true,
    imports: [],
    template: `
        <div class="load-chart-wrap">
            <span class="load-chart-label">WEEKLY LOAD</span>
            <canvas #canvas class="load-canvas"></canvas>
        </div>
    `,
    styles: [
        `
            .load-chart-wrap {
                padding: 8px 0 4px;
            }
            .load-chart-label {
                font-size: 10px;
                letter-spacing: 0.12em;
                color: var(--text-muted, #666);
                text-transform: uppercase;
                display: block;
                margin-bottom: 6px;
            }
            .load-canvas {
                width: 100%;
                height: 72px;
                display: block;
            }
        `,
    ],
})
export class TrainingLoadChartComponent implements OnChanges, AfterViewInit {
    @Input() workoutsByDay: Map<string, ScheduledWorkout[]> = new Map();
    @Input() days: DayInfo[] = [];

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

    private ready = false;

    ngAfterViewInit(): void {
        this.ready = true;
        this.draw();
    }

    ngOnChanges(): void {
        if (this.ready) this.draw();
    }

    private draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || this.days.length === 0) return;

        const W = (canvas.width = canvas.offsetWidth || 560);
        const H = (canvas.height = canvas.offsetHeight || 72);

        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        const accent =
            getComputedStyle(document.documentElement)
                .getPropertyValue('--accent-color')
                .trim() || '#00d4ff';

        const marginL = 0;
        const marginB = 18;
        const chartH = H - marginB - 4;
        const barSlot = (W - marginL) / 7;

        const dailyData = this.days.map((day) => {
            const workouts = this.workoutsByDay.get(day.key) || [];
            const completedLoad = workouts
                .filter((w) => w.status === 'COMPLETED')
                .reduce(
                    (sum, w) =>
                        sum + (w.tss || (w.totalDurationSeconds ? w.totalDurationSeconds / 60 : 0)),
                    0
                );
            const pendingLoad = workouts
                .filter((w) => w.status !== 'COMPLETED')
                .reduce(
                    (sum, w) =>
                        sum + (w.tss || (w.totalDurationSeconds ? w.totalDurationSeconds / 60 : 0)),
                    0
                );
            return { day, completedLoad, pendingLoad };
        });

        const maxLoad = Math.max(...dailyData.map((d) => d.completedLoad + d.pendingLoad), 1);

        const DAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

        dailyData.forEach((d, i) => {
            const x = marginL + i * barSlot + barSlot * 0.15;
            const bw = barSlot * 0.7;
            const totalH = chartH * ((d.completedLoad + d.pendingLoad) / maxLoad);
            const completedH = chartH * (d.completedLoad / maxLoad);

            if (d.pendingLoad > 0) {
                ctx.globalAlpha = 0.22;
                ctx.fillStyle = accent;
                ctx.fillRect(x, H - marginB - totalH, bw, totalH);
            }

            if (d.completedLoad > 0) {
                ctx.globalAlpha = 1;
                ctx.fillStyle = accent;
                ctx.fillRect(x, H - marginB - completedH, bw, completedH);
            }

            if (d.day.isToday) {
                ctx.globalAlpha = 1;
                ctx.fillStyle = accent;
                ctx.fillRect(x, H - marginB, bw, 2);
            }

            ctx.globalAlpha = d.day.isToday ? 1 : 0.5;
            ctx.fillStyle = '#ffffff';
            ctx.font = `${d.day.isToday ? 'bold ' : ''}10px monospace`;
            ctx.textAlign = 'center';
            ctx.fillText(DAY_LABELS[i], x + bw / 2, H - 3);
        });

        ctx.globalAlpha = 1;
    }
}
