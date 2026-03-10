import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  PacingService,
  AthleteProfile,
  PacingPlanResponse,
  PacingSegment,
} from '../../../services/pacing.service';
import { AuthService } from '../../../services/auth.service';
import { ElevationChartComponent } from './elevation-chart/elevation-chart.component';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-pacing-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ElevationChartComponent],
  templateUrl: './pacing-page.component.html',
  styleUrl: './pacing-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacingPageComponent implements OnInit {
  private pacingService = inject(PacingService);
  private authService = inject(AuthService);

  pacingPlan$ = this.pacingService.pacingPlan$;
  loading$ = this.pacingService.loading$;
  error$ = this.pacingService.error$;
  user$ = this.authService.user$;

  // Form state
  gpxFile: File | null = null;
  gpxFileName = '';
  discipline = 'BOTH';
  profile: AthleteProfile = {
    fatigueResistance: 0.5,
    nutritionPreference: 'MIXED',
    temperature: 20,
    windSpeed: 0,
  };

  isDragging = false;
  activeTab = 'BIKE';
  private errorSubject = new BehaviorSubject<string | null>(null);
  localError$ = this.errorSubject.asObservable();

  ngOnInit(): void {
    this.pacingService.getDefaults().subscribe({
      next: (defaults) => {
        this.profile = { ...this.profile, ...defaults };
      },
      error: () => {
        // Defaults not available; user fills manually
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.setFile(input.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.setFile(event.dataTransfer.files[0]);
    }
  }

  private setFile(file: File): void {
    if (!file.name.toLowerCase().endsWith('.gpx')) {
      this.errorSubject.next('Please select a .gpx file');
      return;
    }
    this.gpxFile = file;
    this.gpxFileName = file.name;
    this.errorSubject.next(null);
  }

  generate(): void {
    if (!this.gpxFile) {
      this.errorSubject.next('Please upload a GPX file');
      return;
    }
    this.errorSubject.next(null);

    this.pacingService.generatePacingPlan(this.gpxFile, this.profile, this.discipline).subscribe({
      error: (err) => {
        const msg = err.error?.error || err.message || 'Failed to generate pacing plan';
        this.errorSubject.next(msg);
      },
    });
  }

  getActiveSegments(plan: PacingPlanResponse): PacingSegment[] {
    if (this.activeTab === 'BIKE' && plan.bikeSegments?.length) {
      return plan.bikeSegments;
    }
    if (this.activeTab === 'RUN' && plan.runSegments?.length) {
      return plan.runSegments;
    }
    return plan.bikeSegments || plan.runSegments || [];
  }

  formatTime(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.round(seconds % 60);
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  formatDistance(meters: number): string {
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }

  clearPlan(): void {
    this.pacingService.clearPlan();
    this.gpxFile = null;
    this.gpxFileName = '';
  }
}
