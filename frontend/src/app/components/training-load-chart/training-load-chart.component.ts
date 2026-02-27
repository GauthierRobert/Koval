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

const SPORT_COLORS: Record<string, string> = {
    CYCLING: '#FF9D00',
    RUNNING: '#34D399',
    SWIMMING: '#60A5FA',
    BRICK: '#A78BFA',
    GYM: '#F472B6',
};

function getSportColor(sport?: string): string {
    return SPORT_COLORS[sport || ''] || '#FF9D00';
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

        const marginB = 18;
        const chartH = H - marginB - 4;
        const barSlot = W / 7;

        // Build per-day, per-sport load data
        interface SportLoad {
            sport: string;
            color: string;
            load: number;
        }

        const dailyData = this.days.map((day) => {
            const workouts = this.workoutsByDay.get(day.key) || [];
            const sportMap = new Map<string, { completed: number; pending: number }>();

            for (const w of workouts) {
                const sport = w.sportType || 'CYCLING';
                const entry = sportMap.get(sport) || { completed: 0, pending: 0 };
                const load = w.tss || (w.totalDurationSeconds ? w.totalDurationSeconds / 60 : 0);
                if (w.status === 'COMPLETED') {
                    entry.completed += load;
                } else {
                    entry.pending += load;
                }
                sportMap.set(sport, entry);
            }

            const totalLoad = Array.from(sportMap.values()).reduce(
                (sum, e) => sum + e.completed + e.pending,
                0,
            );

            return { day, sportMap, totalLoad };
        });

        const maxLoad = Math.max(...dailyData.map((d) => d.totalLoad), 1);
        const DAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

        dailyData.forEach((d, i) => {
            const x = i * barSlot + barSlot * 0.15;
            const bw = barSlot * 0.7;
            const totalBarH = chartH * (d.totalLoad / maxLoad);

            // Stack sport segments bottom-up
            let y = H - marginB;
            const sportEntries = Array.from(d.sportMap.entries());

            for (const [sport, entry] of sportEntries) {
                const color = getSportColor(sport);

                // Completed portion (solid)
                if (entry.completed > 0) {
                    const segH = chartH * (entry.completed / maxLoad);
                    ctx.globalAlpha = 1;
                    ctx.fillStyle = color;
                    ctx.fillRect(x, y - segH, bw, segH);
                    y -= segH;
                }

                // Pending portion (translucent)
                if (entry.pending > 0) {
                    const segH = chartH * (entry.pending / maxLoad);
                    ctx.globalAlpha = 0.25;
                    ctx.fillStyle = color;
                    ctx.fillRect(x, y - segH, bw, segH);
                    y -= segH;
                }
            }

            // Load value label inside bar (if wide enough)
            if (d.totalLoad > 0 && totalBarH > 14 && bw > 20) {
                ctx.globalAlpha = 1;
                ctx.fillStyle = '#000';
                ctx.font = 'bold 8px monospace';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';
                ctx.fillText(
                    Math.round(d.totalLoad).toString(),
                    x + bw / 2,
                    H - marginB - totalBarH + 2,
                );
            }

            // Today marker
            if (d.day.isToday) {
                ctx.globalAlpha = 1;
                ctx.fillStyle = '#fff';
                ctx.fillRect(x, H - marginB, bw, 2);
            }

            // Day label
            ctx.globalAlpha = d.day.isToday ? 1 : 0.5;
            ctx.fillStyle = '#ffffff';
            ctx.font = `${d.day.isToday ? 'bold ' : ''}10px monospace`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'alphabetic';
            ctx.fillText(DAY_LABELS[i], x + bw / 2, H - 3);
        });

        ctx.globalAlpha = 1;
    }
}
