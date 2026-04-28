import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  TemplateRef,
  ViewChild,
  contentChild,
  inject,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {SportIconComponent} from '../sport-icon/sport-icon.component';

export type TimelineSport =
  | 'CYCLING'
  | 'RUNNING'
  | 'SWIMMING'
  | 'TRIATHLON'
  | 'OTHER'
  | string;
export type TimelinePriority = 'A' | 'B' | 'C';

export interface TimelineItem<T = unknown> {
  id: string;
  title: string;
  sport: TimelineSport;
  raceDate?: string;
  priority?: TimelinePriority;
  isPrimary?: boolean;
  data?: T;
}

type LaneKey = 'run' | 'tri' | 'bike';

interface TimelineMarker<T> extends TimelineItem<T> {
  _x: number;
  _lane: LaneKey;
  _statusKey: 'A' | 'B' | 'C' | 'PASSED';
  _statusLabel: string;
  _short: string;
  _dateShort: string;
  _passed: boolean;
  _above: boolean;
}

interface LaneDef {
  key: LaneKey;
  label: string;
  icon: 'RUNNING' | 'CYCLING' | 'SWIMMING' | 'BRICK';
}

@Component({
  selector: 'app-goal-timeline',
  standalone: true,
  imports: [CommonModule, SportIconComponent],
  templateUrl: './goal-timeline.component.html',
  styleUrl: './goal-timeline.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalTimelineComponent<T = unknown> implements AfterViewInit, OnDestroy {
  @Input({required: true}) items: TimelineItem<T>[] = [];
  @Input() panelTitle = 'Roadmap 12 mois';
  @Input() showHeader = true;
  @Input() showLegend = true;
  @Input() emptyLaneLabel = 'Aucun objectif sur cette voie';

  @Output() itemClick = new EventEmitter<TimelineItem<T>>();

  cardFooterTpl = contentChild<TemplateRef<{$implicit: TimelineItem<T>}>>('cardFooter');

  @ViewChild('trackRef', {read: ElementRef}) trackRef?: ElementRef<HTMLElement>;

  readonly lanes: LaneDef[] = [
    {key: 'run', label: 'RUN', icon: 'RUNNING'},
    {key: 'tri', label: 'TRI', icon: 'BRICK'},
    {key: 'bike', label: 'BIKE', icon: 'CYCLING'},
  ];

  private readonly window = this.computeWindow();
  readonly months = this.buildMonths(this.window.start);
  readonly windowStartLabel = this.monthLong(this.window.start);
  readonly windowEndLabel = this.monthLong(this.window.end);

  private readonly cdr = inject(ChangeDetectorRef);
  private resizeObserver?: ResizeObserver;
  private trackWidthPx = 800;

  get todayX(): number {
    const span = this.window.end.getTime() - this.window.start.getTime();
    const now = Date.now();
    return Math.max(0, Math.min(100, ((now - this.window.start.getTime()) / span) * 100));
  }

  get todayShort(): string {
    return this.formatToday(new Date());
  }

  get markersByLane(): Record<LaneKey, TimelineMarker<T>[]> {
    return this.buildMarkers(this.items, this.window.start, this.window.end);
  }

  onMarkerClick(marker: TimelineMarker<T>): void {
    this.itemClick.emit(marker);
  }

  ngAfterViewInit(): void {
    if (typeof ResizeObserver === 'undefined' || !this.trackRef) return;
    this.resizeObserver = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 0;
      if (w > 0 && Math.abs(w - this.trackWidthPx) > 4) {
        this.trackWidthPx = w;
        this.cdr.markForCheck();
      }
    });
    this.resizeObserver.observe(this.trackRef.nativeElement);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  private get collisionThresholdPct(): number {
    const cardMaxPx = this.trackWidthPx < 480 ? 110 : 150;
    const buffer = 12;
    return ((cardMaxPx + buffer) / Math.max(this.trackWidthPx, 1)) * 100;
  }

  // ── Window / months ────────────────────────────────────────────────

  private computeWindow(): {start: Date; end: Date} {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    const end = new Date(start.getFullYear() + 1, start.getMonth(), 1);
    return {start, end};
  }

  private buildMonths(start: Date): {label: string}[] {
    const months: {label: string}[] = [];
    for (let i = 0; i <= 12; i++) {
      const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
      months.push({label: this.monthShort(d)});
    }
    return months;
  }

  // ── Marker building ────────────────────────────────────────────────

  private buildMarkers(
    items: TimelineItem<T>[],
    start: Date,
    end: Date,
  ): Record<LaneKey, TimelineMarker<T>[]> {
    const span = end.getTime() - start.getTime();
    const lanes: Record<LaneKey, TimelineMarker<T>[]> = {run: [], tri: [], bike: []};

    for (const item of items) {
      const d = this.parseDate(item.raceDate);
      if (!d) continue;
      if (d < start || d > end) continue;
      const x = ((d.getTime() - start.getTime()) / span) * 100;
      const lane = this.laneOfSport(item.sport);
      const passed = !this.isUpcoming(d);
      lanes[lane].push({
        ...item,
        _x: x,
        _lane: lane,
        _passed: passed,
        _statusKey: passed ? 'PASSED' : (item.priority ?? 'C'),
        _statusLabel: passed ? '✓' : (item.priority ?? 'C'),
        _short: this.shortLabelFor(item),
        _dateShort: this.formatDateShort(item.raceDate),
        _above: false,
      });
    }

    const threshold = this.collisionThresholdPct;
    for (const key of Object.keys(lanes) as LaneKey[]) {
      lanes[key] = this.resolveOverlap(lanes[key], threshold);
    }
    return lanes;
  }

  private resolveOverlap(
    markers: TimelineMarker<T>[],
    thresholdPct: number,
  ): TimelineMarker<T>[] {
    if (markers.length === 0) return markers;
    const sorted = [...markers].sort((a, b) => a._x - b._x);
    let lastBelow = -Infinity;
    let lastAbove = -Infinity;
    for (const m of sorted) {
      const distBelow = m._x - lastBelow;
      const distAbove = m._x - lastAbove;
      if (distBelow >= thresholdPct) {
        m._above = false;
        lastBelow = m._x;
      } else if (distAbove >= thresholdPct) {
        m._above = true;
        lastAbove = m._x;
      } else {
        m._above = distAbove > distBelow;
        if (m._above) lastAbove = m._x;
        else lastBelow = m._x;
      }
    }
    return sorted;
  }

  private laneOfSport(sport: TimelineSport): LaneKey {
    switch (sport) {
      case 'RUNNING':
        return 'run';
      case 'CYCLING':
        return 'bike';
      case 'TRIATHLON':
      case 'SWIMMING':
        return 'tri';
      default:
        return 'bike';
    }
  }

  private shortLabelFor(item: TimelineItem<T>): string {
    const text = `${item.title ?? ''}`.toLowerCase();
    if (item.sport === 'RUNNING') {
      if (/marathon|42/.test(text)) return 'MAR';
      if (/semi|21|half/.test(text)) return '21K';
      if (/10\s?k|10km/.test(text)) return '10K';
      if (/5\s?k|5km/.test(text)) return '5K';
      return 'RUN';
    }
    if (item.sport === 'CYCLING') {
      if (/etape|granfondo|gravel|cyclo/.test(text)) return 'GRF';
      return 'BIKE';
    }
    if (item.sport === 'SWIMMING') return 'SWIM';
    if (item.sport === 'TRIATHLON') {
      if (/ironman|140\.6/.test(text)) return 'IM';
      if (/70\.3|half/.test(text)) return '70.3';
      if (/olympic|olympique/.test(text)) return 'OLY';
      if (/sprint/.test(text)) return 'SPR';
      return 'TRI';
    }
    return '—';
  }

  private parseDate(dateStr: string | undefined | null): Date | null {
    if (!dateStr) return null;
    const direct = new Date(dateStr);
    if (!isNaN(direct.getTime())) return direct;
    const padded = new Date(dateStr + 'T00:00:00');
    if (!isNaN(padded.getTime())) return padded;
    return null;
  }

  private isUpcoming(d: Date): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(d);
    target.setHours(0, 0, 0, 0);
    return target.getTime() >= today.getTime();
  }

  // ── Formatting ─────────────────────────────────────────────────────

  private monthShort(d: Date): string {
    return d.toLocaleDateString('fr-FR', {month: 'short'}).replace('.', '').toUpperCase().slice(0, 3);
  }

  private monthLong(d: Date): string {
    return d.toLocaleDateString('fr-FR', {month: 'long', year: 'numeric'});
  }

  formatDateShort(dateStr: string | undefined | null): string {
    const d = this.parseDate(dateStr);
    if (!d) return 'Date à définir';
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  private formatToday(d: Date): string {
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    return `${day} ${month}`;
  }
}
