import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';
import { cssToRgb } from '../../session-analysis/power-curve-chart/power-curve-chart.utils';
import { formatTimeText } from '../../../shared/format/format.utils';

interface RadarAxis {
  key: string;
  label: string;
  values: (number | null)[];
  max: number;
  format: 'int' | 'duration' | 'two-decimals' | 'distance';
}

@Component({
  selector: 'app-comparison-metrics-radar',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-metrics-radar.component.html',
  styleUrl: './comparison-metrics-radar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonMetricsRadarComponent implements AfterViewInit, OnChanges {
  @Input({ required: true }) sessions: ComparisonSessionEntry[] = [];
  @Input() colors: string[] = [];

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private translate = inject(TranslateService);
  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    this.render();
    if (!this.resizeObserver) {
      this.resizeObserver = new ResizeObserver(() => this.render());
      this.resizeObserver.observe(this.canvasRef.nativeElement);
    }
  }

  ngOnChanges(_: SimpleChanges): void {
    if (this.canvasRef) this.render();
  }

  private buildAxes(): RadarAxis[] {
    const s = this.sessions;
    const t = (k: string) => this.translate.instant(k);
    const candidates: Omit<RadarAxis, 'max'>[] = [
      {
        key: 'duration',
        label: t('SESSION_COMPARE.METRIC_DURATION'),
        values: s.map((x) => x.totalDurationSeconds),
        format: 'duration',
      },
      {
        key: 'distance',
        label: t('SESSION_COMPARE.METRIC_DISTANCE'),
        values: s.map((x) => x.totalDistance),
        format: 'distance',
      },
      {
        key: 'tss',
        label: t('SESSION_COMPARE.METRIC_TSS'),
        values: s.map((x) => x.tss),
        format: 'int',
      },
      {
        key: 'if',
        label: t('SESSION_COMPARE.METRIC_IF'),
        values: s.map((x) => x.intensityFactor),
        format: 'two-decimals',
      },
      {
        key: 'np',
        label: t('SESSION_COMPARE.METRIC_NP'),
        values: s.map((x) => x.normalizedPower ?? x.avgPower),
        format: 'int',
      },
      {
        key: 'avg-hr',
        label: t('SESSION_COMPARE.METRIC_AVG_HR'),
        values: s.map((x) => x.avgHR),
        format: 'int',
      },
      {
        key: 'avg-cad',
        label: t('SESSION_COMPARE.METRIC_AVG_CAD'),
        values: s.map((x) => x.avgCadence),
        format: 'int',
      },
      {
        key: 'avg-speed',
        label: t('SESSION_COMPARE.METRIC_AVG_SPEED'),
        values: s.map((x) => x.avgSpeed),
        format: 'two-decimals',
      },
    ];
    return candidates
      .filter((a) => a.values.some((v) => v != null && v > 0))
      .map((a) => ({
        ...a,
        max: Math.max(...a.values.map((v) => v ?? 0)),
      }));
  }

  formatValue(format: RadarAxis['format'], v: number | null): string {
    if (v == null) return '—';
    switch (format) {
      case 'duration':
        return formatTimeText(v);
      case 'distance':
        return (v / 1000).toFixed(1) + ' km';
      case 'two-decimals':
        return v.toFixed(2);
      default:
        return Math.round(v).toString();
    }
  }

  private render(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.max(1, window.devicePixelRatio || 1);
    const cssW = canvas.clientWidth;
    const cssH = canvas.clientHeight;
    if (cssW <= 0 || cssH <= 0) return;
    const targetW = Math.round(cssW * dpr);
    const targetH = Math.round(cssH * dpr);
    if (canvas.width !== targetW) canvas.width = targetW;
    if (canvas.height !== targetH) canvas.height = targetH;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssW, cssH);

    const axes = this.buildAxes();
    const n = axes.length;
    if (n < 3) {
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(
        this.translate.instant('SESSION_COMPARE.RADAR_INSUFFICIENT'),
        cssW / 2,
        cssH / 2,
      );
      return;
    }

    const cx = cssW / 2;
    const cy = cssH / 2 + 4;
    const radius = Math.max(40, Math.min(cssW, cssH) / 2 - 64);

    const angleFor = (i: number) => -Math.PI / 2 + (i * 2 * Math.PI) / n;

    const grid = 'rgba(255,255,255,0.10)';
    const gridStrong = 'rgba(255,255,255,0.20)';
    const textColor = 'rgba(255,255,255,0.70)';

    ctx.lineWidth = 1;
    const rings = 4;
    for (let r = 1; r <= rings; r++) {
      const rr = (radius * r) / rings;
      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const a = angleFor(i);
        const x = cx + rr * Math.cos(a);
        const y = cy + rr * Math.sin(a);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.closePath();
      ctx.strokeStyle = r === rings ? gridStrong : grid;
      ctx.stroke();
    }

    ctx.strokeStyle = grid;
    for (let i = 0; i < n; i++) {
      const a = angleFor(i);
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(cx + radius * Math.cos(a), cy + radius * Math.sin(a));
      ctx.stroke();
    }

    ctx.fillStyle = textColor;
    ctx.font = '11px sans-serif';
    ctx.textBaseline = 'middle';
    for (let i = 0; i < n; i++) {
      const a = angleFor(i);
      const lx = cx + (radius + 22) * Math.cos(a);
      const ly = cy + (radius + 22) * Math.sin(a);
      const cos = Math.cos(a);
      if (Math.abs(cos) < 0.15) ctx.textAlign = 'center';
      else if (cos < 0) ctx.textAlign = 'right';
      else ctx.textAlign = 'left';
      ctx.fillText(axes[i].label, lx, ly);
    }

    for (let s = 0; s < this.sessions.length; s++) {
      const colorCss = this.colors[s] ?? '#888';
      const rgb = cssToRgb(colorCss) ?? [136, 136, 136];
      const points: { x: number; y: number; defined: boolean }[] = [];
      for (let i = 0; i < n; i++) {
        const axis = axes[i];
        const v = axis.values[s];
        const ratio = v == null || axis.max <= 0 ? 0 : v / axis.max;
        const a = angleFor(i);
        points.push({
          x: cx + radius * ratio * Math.cos(a),
          y: cy + radius * ratio * Math.sin(a),
          defined: v != null,
        });
      }

      ctx.beginPath();
      points.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
      ctx.closePath();
      ctx.fillStyle = `rgba(${rgb.join(',')},0.14)`;
      ctx.fill();
      ctx.strokeStyle = `rgb(${rgb.join(',')})`;
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round';
      ctx.stroke();

      for (const p of points) {
        if (!p.defined) continue;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
        ctx.fillStyle = `rgb(${rgb.join(',')})`;
        ctx.fill();
      }
    }
  }
}
