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
import {CommonModule} from '@angular/common';
import {PacingSegment, RouteCoordinate, SegmentRange} from '../../../../services/pacing.service';
import * as L from 'leaflet';

// Fix Leaflet default icon paths for bundled assets
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});

@Component({
  selector: 'app-route-map',
  standalone: true,
  imports: [CommonModule],
  template: `<div #mapContainer class="map-container"></div>`,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        height: 100%;
      }
      .map-container {
        width: 100%;
        height: 100%;
        border-radius: 12px;
        overflow: hidden;
        background: #1a1a2e;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() routeCoordinates: RouteCoordinate[] = [];
  @Input() segments: PacingSegment[] = [];
  @Input() highlightedRange: SegmentRange | null = null;
  @Input() showSpeed = false;
  @Output() segmentHovered = new EventEmitter<number | null>();

  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef<HTMLDivElement>;

  private map: L.Map | null = null;
  private segmentPolylines: L.Polyline[] = [];
  private highlightPolyline: L.Polyline | null = null;
  private startMarker: L.CircleMarker | null = null;
  private finishMarker: L.CircleMarker | null = null;
  private resizeObserver!: ResizeObserver;

  ngAfterViewInit(): void {
    this.initMap();
    this.renderRoute();
    this.resizeObserver = new ResizeObserver(() => {
      this.map?.invalidateSize();
    });
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.map) return;

    if (changes['routeCoordinates'] || changes['segments'] || changes['showSpeed']) {
      this.renderRoute();
    }
    if (changes['highlightedRange']) {
      this.updateHighlight();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  private initMap(): void {
    this.map = L.map(this.mapContainer.nativeElement, {
      zoomControl: true,
      attributionControl: true,
    });

    // CartoDB Dark Matter - free, no API key, matches dark theme
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: 'abcd',
      maxZoom: 19,
    }).addTo(this.map);
  }

  private renderRoute(): void {
    if (!this.map || !this.routeCoordinates?.length || !this.segments?.length) return;

    // Clear previous layers
    this.clearLayers();

    // Build a lookup: for each coordinate, find which segment it belongs to
    // and color by that segment's gradient
    const coords = this.routeCoordinates;
    const segments = this.segments;

    // Draw per-segment polylines colored by gradient
    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i];
      const segCoords = coords.filter(
        (c) => c.distance >= seg.startDistance && c.distance <= seg.endDistance,
      );

      if (segCoords.length < 2) {
        // If not enough coords in this segment, try to include boundary points
        const before = coords.filter((c) => c.distance <= seg.startDistance);
        const after = coords.filter((c) => c.distance >= seg.endDistance);
        if (before.length) segCoords.unshift(before[before.length - 1]);
        if (after.length) segCoords.push(after[0]);
      }

      if (segCoords.length < 2) continue;

      const latLngs: L.LatLngExpression[] = segCoords.map((c) => [c.lat, c.lon] as L.LatLngTuple);
      const color = this.showSpeed && seg.estimatedSpeedKmh
        ? this.speedColor(seg.estimatedSpeedKmh)
        : this.gradientColor(seg.gradient);

      const polyline = L.polyline(latLngs, {
        color,
        weight: 4,
        opacity: 0.85,
      }).addTo(this.map!);

      // Hover interaction
      polyline.on('mouseover', () => {
        polyline.setStyle({ weight: 7, opacity: 1 });
        this.segmentHovered.emit(i);
      });
      polyline.on('mouseout', () => {
        polyline.setStyle({ weight: 4, opacity: 0.85 });
        this.segmentHovered.emit(null);
      });

      this.segmentPolylines.push(polyline);
    }

    // Start marker (green circle)
    const first = coords[0];
    this.startMarker = L.circleMarker([first.lat, first.lon], {
      radius: 8,
      color: '#fff',
      fillColor: '#34d399',
      fillOpacity: 1,
      weight: 2,
    })
      .bindTooltip('Start', { permanent: false })
      .addTo(this.map);

    // Finish marker (red circle)
    const last = coords[coords.length - 1];
    this.finishMarker = L.circleMarker([last.lat, last.lon], {
      radius: 8,
      color: '#fff',
      fillColor: '#ef4444',
      fillOpacity: 1,
      weight: 2,
    })
      .bindTooltip('Finish', { permanent: false })
      .addTo(this.map);

    // Auto-zoom to fit the route
    const allLatLngs: L.LatLngExpression[] = coords.map((c) => [c.lat, c.lon] as L.LatLngTuple);
    const bounds = L.latLngBounds(allLatLngs);
    this.map.fitBounds(bounds, { padding: [30, 30] });
  }

  private updateHighlight(): void {
    if (!this.map) return;

    // Remove previous highlight
    if (this.highlightPolyline) {
      this.highlightPolyline.remove();
      this.highlightPolyline = null;
    }

    if (!this.highlightedRange || !this.segments?.length || !this.routeCoordinates?.length) {
      return;
    }

    const startSeg = this.segments[this.highlightedRange.start];
    const endSeg = this.segments[Math.min(this.highlightedRange.end, this.segments.length - 1)];
    if (!startSeg || !endSeg) return;

    const rangeStart = startSeg.startDistance;
    const rangeEnd = endSeg.endDistance;

    const segCoords = this.routeCoordinates.filter(
      (c) => c.distance >= rangeStart && c.distance <= rangeEnd,
    );

    // Include boundary points
    const before = this.routeCoordinates.filter((c) => c.distance <= rangeStart);
    const after = this.routeCoordinates.filter((c) => c.distance >= rangeEnd);
    if (before.length && (segCoords.length === 0 || segCoords[0] !== before[before.length - 1])) {
      segCoords.unshift(before[before.length - 1]);
    }
    if (after.length && (segCoords.length === 0 || segCoords[segCoords.length - 1] !== after[0])) {
      segCoords.push(after[0]);
    }

    if (segCoords.length < 2) return;

    const latLngs: L.LatLngExpression[] = segCoords.map((c) => [c.lat, c.lon] as L.LatLngTuple);
    this.highlightPolyline = L.polyline(latLngs, {
      color: '#fff',
      weight: 8,
      opacity: 0.6,
    }).addTo(this.map);

    // Send highlight below segment polylines
    this.highlightPolyline.bringToBack();
  }

 private gradientColor(gradient: number): string {
    const g = gradient;
    if (g > 12) return '#7c3aed';       // extreme climb — deep purple
    if (g > 8) return '#c026d3';        // very steep climb — red-purple
    if (g > 6) return '#dc2626';        // steep climb — red
    if (g > 3) return '#ea580c';        // moderate climb — red-orange
    if (g > 1) return '#f97316';        // slight climb — orange
    if (g >= -1) return '#a0a0a0';      // flat — grey
    if (g >= -3) return '#22c55e';      // slight descent — green
    if (g >= -6) return '#0d9488';      // moderate descent — teal
    if (g >= -10) return '#2563eb';     // steep descent — blue
    return '#1e3a5f';                   // very steep descent — dark blue
  }

  private speedColor(speedKmh: number): string {
    if (speedKmh > 45) return '#7c3aed';    // very fast — purple
    if (speedKmh > 38) return '#2563eb';    // fast — blue
    if (speedKmh > 32) return '#0d9488';    // brisk — teal
    if (speedKmh > 26) return '#34d399';    // moderate-fast — green
    if (speedKmh > 20) return '#a0a0a0';    // moderate — grey
    if (speedKmh > 15) return '#f97316';    // moderate-slow — orange
    if (speedKmh > 10) return '#ea580c';    // slow — red-orange
    return '#dc2626';                        // very slow — red
  }

  private clearLayers(): void {
    for (const pl of this.segmentPolylines) {
      pl.remove();
    }
    this.segmentPolylines = [];

    if (this.highlightPolyline) {
      this.highlightPolyline.remove();
      this.highlightPolyline = null;
    }
    if (this.startMarker) {
      this.startMarker.remove();
      this.startMarker = null;
    }
    if (this.finishMarker) {
      this.finishMarker.remove();
      this.finishMarker = null;
    }
  }
}
