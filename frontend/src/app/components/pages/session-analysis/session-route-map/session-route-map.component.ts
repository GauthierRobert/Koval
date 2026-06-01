import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FitRecord } from '../../../../services/metrics.service';
import { ThemeService } from '../../../../services/theme.service';
import { CARTO_TILE_OPTIONS, tileUrlForTheme } from '../../../shared/leaflet/tile-themes';
import * as L from 'leaflet';

/** A GPS sample with its index back into the full record array (for hover sync + colors). */
interface GpsPoint {
  idx: number;
  lat: number;
  lng: number;
}

/**
 * Leaflet map of a session's GPS track. Sits beside the FIT time-series chart and stretches
 * to its height (a flex-stretch column in the parent); a ResizeObserver keeps Leaflet's canvas
 * in sync as that height changes — e.g. when the athlete toggles extra measures.
 *
 * The track is colored per zone ({@link recordColors}, aligned to the record array). Hovering
 * is bidirectional: moving over the map emits the nearest record index ({@link hoverIndexChange})
 * so the chart can mirror it, and {@link hoverIdx} drives a marker here when the chart is hovered.
 */
@Component({
  selector: 'app-session-route-map',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <div class="route-card glass">
      <div class="route-header section-label">
        {{ 'SESSION_ANALYSIS.CHART_ROUTE_MAP' | translate }}
      </div>
      <div #mapContainer class="map-container"></div>
    </div>
  `,
  styles: [
    `
      /* The host is a flex item the parent row stretches to the chart's height.
         Avoid percentage heights — they collapse against the row's content-based
         height and defeat align-items: stretch — and fill the stretched host with
         flex instead, so the map always spans the full available height. */
      :host {
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .route-card {
        flex: 1;
        display: flex;
        flex-direction: column;
        padding: 0;
        overflow: hidden;
        min-height: 0;
      }
      .route-header {
        padding: var(--space-md) var(--page-padding);
        margin: 0;
      }
      .map-container {
        flex: 1;
        min-height: 240px;
        width: 100%;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionRouteMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() records: FitRecord[] = [];
  /** Per-record zone color, indexed by record. Empty → a single accent-colored track. */
  @Input() recordColors: string[] = [];

  /** Index (into {@link records}) of the externally-hovered sample; null clears the marker. */
  private _hoverIdx: number | null = null;
  @Input() set hoverIdx(v: number | null) {
    this._hoverIdx = v ?? null;
    this.updateHoverMarker();
  }

  /** Emits the record index nearest the cursor as it moves over the map (null on leave). */
  @Output() hoverIndexChange = new EventEmitter<number | null>();

  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef<HTMLDivElement>;

  private themeService = inject(ThemeService);
  private destroyRef = inject(DestroyRef);
  private zone = inject(NgZone);

  private map: L.Map | null = null;
  private tileLayer: L.TileLayer | null = null;
  private segments: L.Polyline[] = [];
  private startMarker: L.CircleMarker | null = null;
  private finishMarker: L.CircleMarker | null = null;
  private hoverMarker: L.CircleMarker | null = null;
  private gps: GpsPoint[] = [];
  private lastEmitted: number | null = null;
  private resizeObserver!: ResizeObserver;

  ngAfterViewInit(): void {
    this.initMap();
    this.renderTrack();
    // Following the chart's height: when the flex column resizes (measures toggled,
    // window resized), Leaflet must recompute its canvas or the map greys out.
    this.resizeObserver = new ResizeObserver(() => this.map?.invalidateSize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
    // Leaflet needs a non-zero container at init; re-fit once layout settles.
    setTimeout(() => {
      this.map?.invalidateSize();
      this.fitToTrack();
    }, 200);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.map && (changes['records'] || changes['recordColors'])) {
      this.renderTrack();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.map?.remove();
    this.map = null;
  }

  private initMap(): void {
    this.map = L.map(this.mapContainer.nativeElement, {
      zoomControl: true,
      attributionControl: true,
    });
    this.map.on('mousemove', (e: L.LeafletMouseEvent) => this.onMapMove(e.latlng));
    this.map.on('mouseout', () => this.emitHover(null));
    this.themeService.theme$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((theme) => {
      if (!this.map) return;
      if (this.tileLayer) this.map.removeLayer(this.tileLayer);
      this.tileLayer = L.tileLayer(tileUrlForTheme(theme), CARTO_TILE_OPTIONS).addTo(this.map);
    });
  }

  private renderTrack(): void {
    if (!this.map) return;
    this.clearLayers();

    this.gps = this.records
      .map((r, idx) => ({ idx, lat: r.lat as number, lng: r.lng as number }))
      .filter(
        (p) => p.lat != null && p.lng != null && !Number.isNaN(p.lat) && !Number.isNaN(p.lng),
      );
    if (this.gps.length < 2) return;

    this.drawColoredSegments();
    this.startMarker = this.dot(this.gps[0], '#34d399');
    this.finishMarker = this.dot(this.gps[this.gps.length - 1], '#ef4444');

    this.fitToTrack();
    this.updateHoverMarker();
  }

  /** Draw the track, coalescing consecutive same-color samples into one polyline each. */
  private drawColoredSegments(): void {
    if (!this.map) return;
    const accent = this.accentColor();
    const colorAt = (i: number): string => this.recordColors[this.gps[i].idx] || accent;
    const latLng = (i: number): L.LatLngTuple => [this.gps[i].lat, this.gps[i].lng];

    let start = 0;
    for (let i = 1; i <= this.gps.length; i++) {
      const boundary = i === this.gps.length || colorAt(i) !== colorAt(start);
      if (!boundary) continue;
      // Include the connecting point (i) so adjacent segments don't show a gap.
      const end = Math.min(i, this.gps.length - 1);
      const pts: L.LatLngTuple[] = [];
      for (let k = start; k <= end; k++) pts.push(latLng(k));
      if (pts.length >= 2) {
        this.segments.push(
          L.polyline(pts, { color: colorAt(start), weight: 4, opacity: 0.9 }).addTo(this.map),
        );
      }
      start = i;
    }
  }

  private dot(p: GpsPoint, fill: string): L.CircleMarker {
    return L.circleMarker([p.lat, p.lng], {
      radius: 7,
      color: '#fff',
      fillColor: fill,
      fillOpacity: 1,
      weight: 2,
    }).addTo(this.map!);
  }

  private fitToTrack(): void {
    if (!this.map || this.gps.length < 2) return;
    const bounds = L.latLngBounds(this.gps.map((p) => [p.lat, p.lng] as L.LatLngTuple));
    this.map.fitBounds(bounds, { padding: [8, 8] });
  }

  /** Move (or hide) the hover marker to the record the chart is pointing at. */
  private updateHoverMarker(): void {
    if (!this.map) return;
    const r = this._hoverIdx != null ? this.records[this._hoverIdx] : null;
    if (!r || r.lat == null || r.lng == null) {
      this.hoverMarker?.remove();
      this.hoverMarker = null;
      return;
    }
    const latlng: L.LatLngTuple = [r.lat, r.lng];
    if (this.hoverMarker) {
      this.hoverMarker.setLatLng(latlng);
    } else {
      this.hoverMarker = L.circleMarker(latlng, {
        radius: 6,
        color: '#fff',
        fillColor: this.accentColor(),
        fillOpacity: 1,
        weight: 2,
      }).addTo(this.map);
    }
    this.hoverMarker.bringToFront();
  }

  /** Nearest GPS sample to the cursor, emitted so the chart mirrors the hover. */
  private onMapMove(latlng: L.LatLng): void {
    if (!this.gps.length) return;
    let best = -1;
    let bestD = Infinity;
    for (const p of this.gps) {
      const dLat = p.lat - latlng.lat;
      const dLng = p.lng - latlng.lng;
      const d = dLat * dLat + dLng * dLng;
      if (d < bestD) {
        bestD = d;
        best = p.idx;
      }
    }
    this.emitHover(best >= 0 ? best : null);
  }

  /** Leaflet events fire outside Angular — re-enter the zone so the chart binding updates. */
  private emitHover(idx: number | null): void {
    if (idx === this.lastEmitted) return;
    this.lastEmitted = idx;
    this.zone.run(() => this.hoverIndexChange.emit(idx));
  }

  private accentColor(): string {
    const v = getComputedStyle(this.mapContainer.nativeElement)
      .getPropertyValue('--accent-color')
      .trim();
    return v || '#ff9d00';
  }

  private clearLayers(): void {
    for (const s of this.segments) s.remove();
    this.segments = [];
    this.startMarker?.remove();
    this.startMarker = null;
    this.finishMarker?.remove();
    this.finishMarker = null;
    this.hoverMarker?.remove();
    this.hoverMarker = null;
  }
}
