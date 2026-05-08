import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import {PmcDataPoint} from '../../../services/metrics.service';
import {RaceGoal} from '../../../services/race-goal.service';
import {addDaysToDate, dateToDays, daysToDate} from './pmc-chart.utils';
import {drawPmcChart} from './pmc-chart-renderer';

@Component({
    selector: 'app-pmc-chart',
    standalone: true,
    imports: [],
    template: `
        <div class="pmc-chart-wrap">
            <canvas #canvas class="pmc-canvas"
                (mousemove)="onMouseMove($event)"
                (mouseleave)="onMouseLeave()"
                (mousedown)="onMouseDown($event)">
            </canvas>
        </div>
    `,
    styles: [`
        :host { display: flex; flex-direction: column; flex: 1; min-height: 0; }
        .pmc-chart-wrap { flex: 1; display: flex; flex-direction: column; min-height: 0; touch-action: pan-y; }
        .pmc-canvas { width: 100%; flex: 1; min-height: 320px; display: block; cursor: crosshair; touch-action: pan-y; }
    `],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PmcChartComponent implements OnChanges, AfterViewInit, OnDestroy {
    @Input() data: PmcDataPoint[] | null = [];
    @Input() goals: RaceGoal[] | null = [];

    @Output() viewRangeChange = new EventEmitter<{start: string, end: string}>();

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;
    private ready = false;
    private hoverIdx: number | null = null;

    private readonly ML = 54;
    private readonly MR = 54;

    private viewStartDate = '';
    private viewEndDate = '';
    private viewInitialized = false;
    private isDragging = false;
    private dragStartX = 0;
    private dragStartViewStartDate = '';
    private dragStartViewEndDate = '';
    private dragMoved = false;
    private readonly MIN_VISIBLE_DAYS = 7;
    private readonly MAX_VISIBLE_DAYS = 365;

    private isTouchDragging = false;
    private touchStartX = 0;
    private touchStartY = 0;
    private touchGesture: 'undecided' | 'pan' | 'scroll' = 'undecided';
    private touchStartViewStartDate = '';
    private touchStartViewEndDate = '';
    private isPinching = false;
    private pinchStartDist = 0;
    private pinchStartSpan = 0;
    private pinchMidX = 0;

    private wheelHandler = (e: WheelEvent) => this.onWheel(e);
    private mouseUpHandler = (e: MouseEvent) => this.onMouseUp(e);
    private touchStartHandler = (e: TouchEvent) => this.onTouchStart(e);
    private touchMoveHandler = (e: TouchEvent) => this.onTouchMove(e);
    private touchEndHandler = (e: TouchEvent) => this.onTouchEnd(e);

    private get visibleIndices(): { start: number; end: number } | null {
        const pts = this.data ?? [];
        if (pts.length < 2 || !this.viewStartDate) return null;
        let s = 0;
        while (s < pts.length && pts[s].date < this.viewStartDate) s++;
        if (s >= pts.length) return null;
        let e = pts.length - 1;
        while (e > s && pts[e].date > this.viewEndDate) e--;
        return s <= e ? { start: s, end: e } : null;
    }

    ngAfterViewInit(): void {
        this.ready = true;
        const canvas = this.canvasRef?.nativeElement;
        if (canvas) {
            canvas.addEventListener('wheel', this.wheelHandler, { passive: false });
            canvas.addEventListener('touchstart', this.touchStartHandler, { passive: false });
            canvas.addEventListener('touchmove', this.touchMoveHandler, { passive: false });
            canvas.addEventListener('touchend', this.touchEndHandler);
        }
        document.addEventListener('mouseup', this.mouseUpHandler);
        this.draw();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['data'] && !this.viewInitialized) {
            const pts = this.data ?? [];
            if (pts.length >= 2) {
                const today = new Date().toISOString().split('T')[0];
                const first = pts[0].date;
                const last = pts[pts.length - 1].date;
                const desiredStart = addDaysToDate(today, -30);
                const desiredEnd = addDaysToDate(today, 30);
                this.viewStartDate = desiredStart < first ? first : desiredStart;
                this.viewEndDate = desiredEnd > last ? last : desiredEnd;
                this.viewInitialized = true;
                this.emitViewRange();
            }
        }
        if (this.ready) this.draw();
    }

    ngOnDestroy(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (canvas) {
            canvas.removeEventListener('wheel', this.wheelHandler);
            canvas.removeEventListener('touchstart', this.touchStartHandler);
            canvas.removeEventListener('touchmove', this.touchMoveHandler);
            canvas.removeEventListener('touchend', this.touchEndHandler);
        }
        document.removeEventListener('mouseup', this.mouseUpHandler);
    }

    onMouseDown(event: MouseEvent): void {
        this.isDragging = false;
        this.dragMoved = false;
        this.dragStartX = event.clientX;
        this.dragStartViewStartDate = this.viewStartDate;
        this.dragStartViewEndDate = this.viewEndDate;
    }

    onMouseMove(event: MouseEvent): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || !this.viewStartDate) return;

        if (event.buttons & 1) {
            const dx = event.clientX - this.dragStartX;
            if (!this.dragMoved && Math.abs(dx) > 3) {
                this.dragMoved = true;
                this.isDragging = true;
                this.hoverIdx = null;
                canvas.style.cursor = 'grabbing';
            }
            if (this.isDragging) {
                const rect = canvas.getBoundingClientRect();
                const span = dateToDays(this.dragStartViewEndDate) - dateToDays(this.dragStartViewStartDate);
                const daysShift = Math.round(-(dx / rect.width) * span);
                this.viewStartDate = addDaysToDate(this.dragStartViewStartDate, daysShift);
                this.viewEndDate = addDaysToDate(this.dragStartViewEndDate, daysShift);
                this.emitViewRange();
                this.draw();
                return;
            }
        }

        if (this.isDragging) return;
        this.updateHoverFromClientX(event.clientX, canvas);
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
        if (this.isDragging) {
            this.isDragging = false;
            this.dragMoved = false;
            const canvas = this.canvasRef?.nativeElement;
            if (canvas) canvas.style.cursor = 'crosshair';
        }
        this.draw();
    }

    private onMouseUp(_event: MouseEvent): void {
        if (this.isDragging) {
            this.isDragging = false;
            this.dragMoved = false;
            const canvas = this.canvasRef?.nativeElement;
            if (canvas) canvas.style.cursor = 'crosshair';
        }
    }

    private onTouchStart(event: TouchEvent): void {
        if (!this.viewStartDate) return;
        if (event.touches.length === 2) {
            event.preventDefault();
            this.isPinching = true;
            this.isTouchDragging = false;
            this.touchGesture = 'pan';
            const t = event.touches;
            this.pinchStartDist = Math.hypot(t[1].clientX - t[0].clientX, t[1].clientY - t[0].clientY);
            this.pinchStartSpan = dateToDays(this.viewEndDate) - dateToDays(this.viewStartDate);
            this.pinchMidX = (t[0].clientX + t[1].clientX) / 2;
            this.touchStartViewStartDate = this.viewStartDate;
            this.touchStartViewEndDate = this.viewEndDate;
        } else if (event.touches.length === 1) {
            this.isTouchDragging = false;
            this.isPinching = false;
            this.touchGesture = 'undecided';
            this.touchStartX = event.touches[0].clientX;
            this.touchStartY = event.touches[0].clientY;
            this.touchStartViewStartDate = this.viewStartDate;
            this.touchStartViewEndDate = this.viewEndDate;
        }
    }

    private onTouchMove(event: TouchEvent): void {
        if (!this.viewStartDate) return;
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        if (this.isPinching && event.touches.length === 2) {
            event.preventDefault();
            const t = event.touches;
            const dist = Math.hypot(t[1].clientX - t[0].clientX, t[1].clientY - t[0].clientY);
            const scale = this.pinchStartDist / dist;
            let newSpan = Math.round(this.pinchStartSpan * scale);
            newSpan = Math.min(this.MAX_VISIBLE_DAYS, Math.max(this.MIN_VISIBLE_DAYS, newSpan));

            const rect = canvas.getBoundingClientRect();
            const cursorFrac = (this.pinchMidX - rect.left) / rect.width;
            const origStart = dateToDays(this.touchStartViewStartDate);
            const origEnd = dateToDays(this.touchStartViewEndDate);
            const anchorD = origStart + cursorFrac * (origEnd - origStart);

            const newStart = Math.round(anchorD - cursorFrac * newSpan);
            this.viewStartDate = daysToDate(newStart);
            this.viewEndDate = daysToDate(newStart + newSpan);
            this.emitViewRange();
            this.draw();
            return;
        }

        if (event.touches.length === 1 && !this.isPinching) {
            const touch = event.touches[0];
            const dx = touch.clientX - this.touchStartX;
            const dy = touch.clientY - this.touchStartY;
            const adx = Math.abs(dx);
            const ady = Math.abs(dy);

            if (this.touchGesture === 'undecided') {
                if (adx < 8 && ady < 8) return;
                if (adx > ady) {
                    this.touchGesture = 'pan';
                } else {
                    this.touchGesture = 'scroll';
                    this.hoverIdx = null;
                    this.draw();
                    return;
                }
            }

            if (this.touchGesture === 'scroll') return;

            if (event.cancelable) event.preventDefault();

            if (!this.isTouchDragging && adx > 5) {
                this.isTouchDragging = true;
                this.hoverIdx = null;
            }

            if (this.isTouchDragging) {
                const rect = canvas.getBoundingClientRect();
                const span = dateToDays(this.touchStartViewEndDate) - dateToDays(this.touchStartViewStartDate);
                const daysShift = Math.round(-(dx / rect.width) * span);
                this.viewStartDate = addDaysToDate(this.touchStartViewStartDate, daysShift);
                this.viewEndDate = addDaysToDate(this.touchStartViewEndDate, daysShift);
                this.emitViewRange();
                this.draw();
            } else {
                this.updateHoverFromClientX(touch.clientX, canvas);
            }
        }
    }

    private onTouchEnd(event: TouchEvent): void {
        if (event.touches.length < 2) this.isPinching = false;
        if (event.touches.length === 0) {
            this.isTouchDragging = false;
            this.touchGesture = 'undecided';
            this.hoverIdx = null;
            this.draw();
        }
    }

    private updateHoverFromClientX(clientX: number, canvas: HTMLCanvasElement): void {
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (clientX - rect.left) * scaleX;

        const vis = this.visibleIndices;
        if (!vis) return;

        const points = this.data!;
        const cW = canvas.width - this.ML - this.MR;
        const viewStartDays = dateToDays(this.viewStartDate);
        const viewSpan = dateToDays(this.viewEndDate) - viewStartDays;
        if (viewSpan <= 0) return;

        const cursorDays = viewStartDays + ((mouseX - this.ML) / cW) * viewSpan;
        let bestIdx = vis.start;
        let bestDist = Math.abs(dateToDays(points[vis.start].date) - cursorDays);
        for (let i = vis.start + 1; i <= vis.end; i++) {
            const dist = Math.abs(dateToDays(points[i].date) - cursorDays);
            if (dist < bestDist) { bestDist = dist; bestIdx = i; }
        }

        if (bestIdx !== this.hoverIdx) {
            this.hoverIdx = bestIdx;
            this.draw();
        }
    }

    private onWheel(event: WheelEvent): void {
        event.preventDefault();
        if (!this.viewStartDate) return;
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const cursorFrac = (event.clientX - rect.left) / rect.width;

        const startD = dateToDays(this.viewStartDate);
        const endD = dateToDays(this.viewEndDate);
        const span = endD - startD;
        const anchorD = startD + cursorFrac * span;

        const factor = event.deltaY > 0 ? 1.1 : 1 / 1.1;
        const newSpan = Math.round(
            Math.min(this.MAX_VISIBLE_DAYS, Math.max(this.MIN_VISIBLE_DAYS, span * factor))
        );

        const newStart = Math.round(anchorD - cursorFrac * newSpan);
        this.viewStartDate = daysToDate(newStart);
        this.viewEndDate = daysToDate(newStart + newSpan);
        this.emitViewRange();
        this.draw();
    }

    private emitViewRange(): void {
        this.viewRangeChange.emit({ start: this.viewStartDate, end: this.viewEndDate });
    }

    private draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;
        drawPmcChart(canvas, {
            data: this.data ?? [],
            goals: this.goals ?? [],
            viewStartDate: this.viewStartDate,
            viewEndDate: this.viewEndDate,
            hoverIdx: this.hoverIdx,
            marginLeft: this.ML,
            marginRight: this.MR,
        });
    }
}
