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
import * as L from 'leaflet';

L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});

export interface MeetingPoint {
  lat: number;
  lon: number;
}

@Component({
  selector: 'app-meeting-point-picker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="picker-wrapper">
      <div #mapContainer class="picker-map"></div>
      @if (lat != null && lon != null) {
        <div class="picker-info">
          <span class="coords">{{ lat.toFixed(5) }}, {{ lon.toFixed(5) }}</span>
          <button class="clear-btn" (click)="clearPoint()">&times;</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .picker-wrapper { display: flex; flex-direction: column; gap: 4px; }
    .picker-map {
      width: 100%;
      height: 180px;
      border-radius: 8px;
      overflow: hidden;
      background: #1a1a2e;
      cursor: crosshair;
    }
    .picker-info {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 10px;
      color: var(--text-muted);
      font-weight: 600;
      letter-spacing: 0.3px;
    }
    .coords { font-family: monospace; }
    .clear-btn {
      background: transparent;
      border: 1px solid var(--glass-border);
      color: var(--text-muted);
      width: 20px;
      height: 20px;
      border-radius: 4px;
      cursor: pointer;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }
    .clear-btn:hover { color: var(--danger-color); border-color: var(--danger-color); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeetingPointPickerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() lat: number | null = null;
  @Input() lon: number | null = null;
  @Output() pointChanged = new EventEmitter<MeetingPoint | null>();

  @ViewChild('mapContainer', {static: true}) mapContainer!: ElementRef<HTMLDivElement>;

  private map: L.Map | null = null;
  private marker: L.Marker | null = null;

  ngAfterViewInit(): void {
    const center: L.LatLngExpression = this.lat != null && this.lon != null
      ? [this.lat, this.lon]
      : [48.8566, 2.3522]; // Default: Paris
    const zoom = this.lat != null ? 14 : 5;

    this.map = L.map(this.mapContainer.nativeElement, {
      center,
      zoom,
      zoomControl: false,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
    }).addTo(this.map);

    L.control.zoom({position: 'bottomright'}).addTo(this.map);

    if (this.lat != null && this.lon != null) {
      this.marker = L.marker([this.lat, this.lon]).addTo(this.map);
    }

    this.map.on('click', (e: L.LeafletMouseEvent) => {
      this.setPoint(e.latlng.lat, e.latlng.lng);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.map) return;
    if (changes['lat'] || changes['lon']) {
      this.updateMarker();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = null;
  }

  private setPoint(lat: number, lon: number): void {
    this.lat = lat;
    this.lon = lon;
    this.updateMarker();
    this.pointChanged.emit({lat, lon});
  }

  clearPoint(): void {
    this.lat = null;
    this.lon = null;
    if (this.marker && this.map) {
      this.map.removeLayer(this.marker);
      this.marker = null;
    }
    this.pointChanged.emit(null);
  }

  private updateMarker(): void {
    if (!this.map) return;
    if (this.lat != null && this.lon != null) {
      if (this.marker) {
        this.marker.setLatLng([this.lat, this.lon]);
      } else {
        this.marker = L.marker([this.lat, this.lon]).addTo(this.map);
      }
    }
  }
}
