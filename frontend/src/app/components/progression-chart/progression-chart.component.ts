import {
    Component,
    Input,
    OnChanges,
    AfterViewInit,
    ViewChild,
    ElementRef,
} from '@angular/core';
import { SavedSession } from '../../services/history.service';

@Component({
    selector: 'app-progression-chart',
    standalone: true,
    imports: [],
    template: `
        <div class="prog-chart-wrap">
            <span class="prog-chart-label">POWER PROGRESSION</span>
            <canvas #canvas class="prog-canvas"></canvas>
        </div>
    `,
    styles: [
        `
            .prog-chart-wrap {
                padding: 8px 12px 4px;
            }
            .prog-chart-label {
                font-size: 10px;
                letter-spacing: 0.12em;
                color: var(--text-muted, #666);
                text-transform: uppercase;
                display: block;
                margin-bottom: 6px;
            }
            .prog-canvas {
                width: 100%;
                height: 96px;
                display: block;
            }
        `,
    ],
})
export class ProgressionChartComponent implements OnChanges, AfterViewInit {
    @Input() sessions: SavedSession[] = [];

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
        if (!canvas) return;

        const W = (canvas.width = canvas.offsetWidth || 300);
        const H = (canvas.height = canvas.offsetHeight || 96);

        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        const last10 = [...this.sessions].slice(0, 10).reverse();
        if (last10.length < 2) {
            ctx.globalAlpha = 0.35;
            ctx.fillStyle = '#ffffff';
            ctx.font = '11px monospace';
            ctx.textAlign = 'center';
            ctx.fillText('Complete more sessions to see progression', W / 2, H / 2);
            ctx.globalAlpha = 1;
            return;
        }

        const accent =
            getComputedStyle(document.documentElement)
                .getPropertyValue('--accent-color')
                .trim() || '#00d4ff';

        const marginL = 4;
        const marginR = 4;
        const marginT = 8;
        const marginB = 20;
        const chartW = W - marginL - marginR;
        const chartH = H - marginT - marginB;

        const powers = last10.map((s) => s.avgPower);
        const minP = Math.max(0, Math.min(...powers) * 0.85);
        const maxP = Math.max(...powers) * 1.1 || 1;

        const xStep = chartW / (last10.length - 1);
        const pts = last10.map((s, i) => ({
            x: marginL + i * xStep,
            y: marginT + chartH * (1 - (s.avgPower - minP) / (maxP - minP)),
        }));

        // Filled area
        ctx.beginPath();
        ctx.moveTo(pts[0].x, H - marginB);
        pts.forEach((p) => ctx.lineTo(p.x, p.y));
        ctx.lineTo(pts[pts.length - 1].x, H - marginB);
        ctx.closePath();
        const grad = ctx.createLinearGradient(0, marginT, 0, H - marginB);
        grad.addColorStop(0, accent + '55');
        grad.addColorStop(1, accent + '00');
        ctx.fillStyle = grad;
        ctx.fill();

        // Line
        ctx.beginPath();
        ctx.moveTo(pts[0].x, pts[0].y);
        pts.forEach((p) => ctx.lineTo(p.x, p.y));
        ctx.strokeStyle = accent;
        ctx.lineWidth = 2;
        ctx.stroke();

        // Dots and labels
        pts.forEach((p, i) => {
            ctx.beginPath();
            ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
            ctx.fillStyle = accent;
            ctx.fill();

            const dateStr = new Date(last10[i].date).toLocaleDateString('en', {
                month: 'numeric',
                day: 'numeric',
            });
            ctx.fillStyle = 'rgba(255,255,255,0.5)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'center';
            ctx.fillText(dateStr, p.x, H - 4);
        });
    }
}
