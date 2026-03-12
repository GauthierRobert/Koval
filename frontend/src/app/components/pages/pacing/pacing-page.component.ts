import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {AthleteProfile, PacingPlanResponse, PacingSegment, PacingService, RouteCoordinate, SegmentRange,} from '../../../services/pacing.service';
import {AuthService} from '../../../services/auth.service';
import {Race, RaceService, SimulationRequest} from '../../../services/race.service';
import {ElevationChartComponent} from './elevation-chart/elevation-chart.component';
import {RouteMapComponent} from './route-map/route-map.component';
import {BehaviorSubject} from 'rxjs';

interface NutritionEvent {
  distance: number;
  suggestion: string;
}

interface RacePlanGroup {
  index: number;
  segmentStart: number;
  segmentEnd: number;
  startDistance: number;
  endDistance: number;
  targetPower?: number;
  targetPace?: string;
  meanGradient: number;
  elevationChange: number;
  totalTime: number;
  meanSpeed?: number;
  fatigueStart: number;
  fatigueEnd: number;
  nutritionEvents: NutritionEvent[];
  terrainLabel: string;
}

@Component({
  selector: 'app-pacing-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ElevationChartComponent, RouteMapComponent],
  templateUrl: './pacing-page.component.html',
  styleUrl: './pacing-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacingPageComponent implements OnInit {
  private pacingService = inject(PacingService);
  private authService = inject(AuthService);
  private raceService = inject(RaceService);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  pacingPlan$ = this.pacingService.pacingPlan$;
  loading$ = this.pacingService.loading$;
  error$ = this.pacingService.error$;
  user$ = this.authService.user$;

  // Race pre-fill from query params
  linkedRace: Race | null = null;
  linkedGoalId: string | null = null;

  // Form state — single GPX (for BIKE or RUN single discipline)
  gpxFile: File | null = null;
  gpxFileName = '';

  // Dual GPX (for TRIATHLON — separate bike/run courses)
  bikeGpxFile: File | null = null;
  bikeGpxFileName = '';
  runGpxFile: File | null = null;
  runGpxFileName = '';

  // Loop counts
  bikeLoops = 1;
  runLoops = 1;

  // Drag state per dropzone
  isDragging = false;
  isDraggingBike = false;
  isDraggingRun = false;

  discipline = 'TRIATHLON';
  profile: AthleteProfile = {
    // TODO: Re-enable fatigue resistance once backend integrates it
    fatigueResistance: 0.5,
    nutritionPreference: 'MIXED',
    temperature: 20,
    windSpeed: 0,
    bikeType: 'ROAD_AERO',
  };

  // Percentage-based target inputs
  targetPowerPct: number | null = null;
  targetPacePct: number | null = null;

  activeTab = 'BIKE';
  highlightedGroup: number | null = null;
  showSettingsModal = false;
  private errorSubject = new BehaviorSubject<string | null>(null);
  localError$ = this.errorSubject.asObservable();

  private highlightedRangeSubject = new BehaviorSubject<SegmentRange | null>(null);
  highlightedRange$ = this.highlightedRangeSubject.asObservable();

  private groupedPlanCache: { key: string; result: RacePlanGroup[] } | null = null;

  onSegmentHover(index: number | null): void {
    this.highlightedRangeSubject.next(index != null ? { start: index, end: index } : null);
  }

  onGroupHover(group: RacePlanGroup | null, index: number | null): void {
    this.highlightedGroup = index;
    this.highlightedRangeSubject.next(group ? { start: group.segmentStart, end: group.segmentEnd } : null);
  }

  getGroupedPlan(segments: PacingSegment[]): RacePlanGroup[] {
    const key = segments.length + ':' + (segments[0]?.startDistance ?? '') + ':' + (segments[segments.length - 1]?.endDistance ?? '');
    if (this.groupedPlanCache?.key === key) {
      return this.groupedPlanCache.result;
    }

    const groups: RacePlanGroup[] = [];
    if (!segments.length) return groups;

    let groupStart = 0;

    for (let i = 1; i <= segments.length; i++) {
      const prev = segments[i - 1];
      const curr = i < segments.length ? segments[i] : null;

      const changed = !curr ||
        (prev.targetPower != null && curr.targetPower !== prev.targetPower) ||
        (prev.targetPace != null && curr.targetPace !== prev.targetPace);

      if (changed) {
        const groupSegments = segments.slice(groupStart, i);
        const first = groupSegments[0];
        const last = groupSegments[groupSegments.length - 1];

        let totalDist = 0;
        let weightedGradient = 0;
        let totalTime = 0;
        const nutritionEvents: NutritionEvent[] = [];

        for (const seg of groupSegments) {
          const segDist = seg.endDistance - seg.startDistance;
          totalDist += segDist;
          weightedGradient += seg.gradient * segDist;
          totalTime += seg.estimatedSegmentTime;

          if (seg.nutritionSuggestion) {
            nutritionEvents.push({
              distance: (seg.startDistance + seg.endDistance) / 2,
              suggestion: seg.nutritionSuggestion,
            });
          }
        }

        const meanGradient = totalDist > 0 ? weightedGradient / totalDist : 0;
        const elevationChange = Math.round(last.elevation - first.elevation);
        const meanSpeed = totalTime > 0 ? (totalDist / 1000) / (totalTime / 3600) : undefined;

        groups.push({
          index: groups.length + 1,
          segmentStart: groupStart,
          segmentEnd: i - 1,
          startDistance: first.startDistance,
          endDistance: last.endDistance,
          targetPower: first.targetPower,
          targetPace: first.targetPace,
          meanGradient,
          elevationChange,
          totalTime,
          meanSpeed,
          fatigueStart: first.cumulativeFatigue,
          fatigueEnd: last.cumulativeFatigue,
          nutritionEvents,
          terrainLabel: this.getTerrainLabel(meanGradient),
        });

        groupStart = i;
      }
    }

    this.groupedPlanCache = { key, result: groups };
    return groups;
  }

  private getTerrainLabel(gradient: number): string {
    if (gradient > 6) return 'Steep Climb';
    if (gradient > 3) return 'Climb';
    if (gradient > 1) return 'Slight Climb';
    if (gradient >= -1) return 'Flat';
    if (gradient >= -3) return 'Slight Descent';
    if (gradient >= -6) return 'Descent';
    return 'Steep Descent';
  }

  ngOnInit(): void {
    this.pacingService.getDefaults().subscribe({
      next: (defaults) => {
        this.profile = { ...this.profile, ...defaults };
        this.cdr.markForCheck();
      },
      error: () => {
        // Defaults not available; user fills manually
      },
    });

    // Check for race pre-fill from query params
    this.route.queryParams.subscribe((params) => {
      const raceId = params['raceId'];
      const goalId = params['goalId'];
      if (raceId) {
        this.linkedGoalId = goalId ?? null;
        this.raceService.getRace(raceId).subscribe({
          next: (race) => {
            this.linkedRace = race;
            // Pre-fill discipline from race sport
            if (race.sport === 'TRIATHLON') this.discipline = 'TRIATHLON';
            else if (race.sport === 'CYCLING') this.discipline = 'BIKE';
            else if (race.sport === 'RUNNING') this.discipline = 'RUN';
            else if (race.sport === 'SWIMMING') this.discipline = 'SWIM';
            // Pre-fill swim distance
            if (race.swimDistanceM) this.profile.swimDistanceM = race.swimDistanceM;
            this.cdr.markForCheck();
          },
        });
      }
    });
  }

  onFileSelected(event: Event, target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.setFile(input.files[0], target);
    }
  }

  onDragOver(event: DragEvent, target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    event.preventDefault();
    if (target === 'bike') this.isDraggingBike = true;
    else if (target === 'run') this.isDraggingRun = true;
    else this.isDragging = true;
  }

  onDragLeave(target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    if (target === 'bike') this.isDraggingBike = false;
    else if (target === 'run') this.isDraggingRun = false;
    else this.isDragging = false;
  }

  onDrop(event: DragEvent, target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    event.preventDefault();
    if (target === 'bike') this.isDraggingBike = false;
    else if (target === 'run') this.isDraggingRun = false;
    else this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.setFile(event.dataTransfer.files[0], target);
    }
  }

  private setFile(file: File, target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    if (!file.name.toLowerCase().endsWith('.gpx')) {
      this.errorSubject.next('Please select a .gpx file');
      return;
    }
    if (target === 'bike') {
      this.bikeGpxFile = file;
      this.bikeGpxFileName = file.name;
    } else if (target === 'run') {
      this.runGpxFile = file;
      this.runGpxFileName = file.name;
    } else {
      this.gpxFile = file;
      this.gpxFileName = file.name;
    }
    this.errorSubject.next(null);
  }

  canGenerate(): boolean {
    // When linked to a race with GPX, no file upload needed
    if (this.linkedRace) {
      if (this.discipline === 'SWIM') return true;
      if (this.discipline === 'TRIATHLON') return !!(this.linkedRace.hasBikeGpx && this.linkedRace.hasRunGpx);
      if (this.discipline === 'BIKE') return !!this.linkedRace.hasBikeGpx;
      if (this.discipline === 'RUN') return !!this.linkedRace.hasRunGpx;
    }
    if (this.discipline === 'SWIM') return true;
    if (this.discipline === 'TRIATHLON') return !!(this.bikeGpxFile && this.runGpxFile);
    // BIKE or RUN — single GPX
    return !!this.gpxFile;
  }

  generate(): void {
    if (!this.canGenerate()) {
      this.errorSubject.next('Please upload the required GPX file(s)');
      return;
    }
    this.errorSubject.next(null);

    // Use race-based generation if linked to a race
    if (this.linkedRace) {
      this.pacingService
        .generateFromRace(
          this.linkedRace.id,
          this.profile,
          this.discipline,
          this.bikeLoops,
          this.runLoops,
        )
        .subscribe({
          next: (plan) => {
            this.setDefaultTab(plan);
            this.saveSimulationRequest();
          },
          error: (err) => {
            const msg = err.error?.error || err.message || 'Failed to generate pacing plan';
            this.errorSubject.next(msg);
          },
        });
      return;
    }

    this.pacingService
      .generatePacingPlan(
        this.gpxFile,
        this.bikeGpxFile,
        this.runGpxFile,
        this.profile,
        this.discipline,
        this.bikeLoops,
        this.runLoops,
      )
      .subscribe({
        next: (plan) => {
          this.setDefaultTab(plan);
        },
        error: (err) => {
          const msg = err.error?.error || err.message || 'Failed to generate pacing plan';
          this.errorSubject.next(msg);
        },
      });
  }

  private saveSimulationRequest(): void {
    if (!this.linkedRace) return;
    const req: SimulationRequest = {
      raceId: this.linkedRace.id,
      goalId: this.linkedGoalId ?? undefined,
      discipline: this.discipline,
      athleteProfile: this.profile,
      bikeLoops: this.bikeLoops,
      runLoops: this.runLoops,
      label: `${this.discipline} - ${this.linkedRace.title}`,
    };
    this.raceService.saveSimulationRequest(req).subscribe();
  }

  needsSwim(): boolean {
    return this.discipline === 'SWIM' || this.discipline === 'TRIATHLON';
  }

  needsBike(): boolean {
    return this.discipline === 'BIKE' || this.discipline === 'TRIATHLON';
  }

  needsRun(): boolean {
    return this.discipline === 'RUN' || this.discipline === 'TRIATHLON';
  }

  onTargetPowerPctChange(pct: number | null): void {
    this.targetPowerPct = pct;
    if (pct && this.profile.ftp) {
      this.profile.targetPowerWatts = Math.round(this.profile.ftp * pct / 100);
    } else {
      this.profile.targetPowerWatts = undefined;
    }
  }

  onTargetPacePctChange(pct: number | null): void {
    this.targetPacePct = pct;
    if (pct && this.profile.thresholdPaceSec) {
      this.profile.targetPaceSecPerKm = Math.round(this.profile.thresholdPaceSec * pct / 100);
    } else {
      this.profile.targetPaceSecPerKm = undefined;
    }
  }

  computedPowerWatts(): number | null {
    if (this.targetPowerPct && this.profile.ftp) {
      return Math.round(this.profile.ftp * this.targetPowerPct / 100);
    }
    return null;
  }

  computedPaceDisplay(): string | null {
    if (this.targetPacePct && this.profile.thresholdPaceSec) {
      const sec = Math.round(this.profile.thresholdPaceSec * this.targetPacePct / 100);
      const m = Math.floor(sec / 60);
      const s = sec % 60;
      return `${m}:${String(s).padStart(2, '0')} /km`;
    }
    return null;
  }

  getAvailableTabs(plan: PacingPlanResponse): string[] {
    const tabs: string[] = [];
    if (plan.swimSummary) tabs.push('SWIM');
    if (plan.bikeSegments?.length) tabs.push('BIKE');
    if (plan.runSegments?.length) tabs.push('RUN');
    return tabs;
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

  getActiveRouteCoordinates(plan: PacingPlanResponse): RouteCoordinate[] | null {
    if (this.activeTab === 'RUN' && plan.runRouteCoordinates?.length) {
      return plan.runRouteCoordinates;
    }
    if (plan.bikeRouteCoordinates?.length) {
      return plan.bikeRouteCoordinates;
    }
    return plan.runRouteCoordinates;
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

  closeSettingsModal(): void {
    this.showSettingsModal = false;
  }

  regenerate(): void {
    if (!this.canGenerate()) {
      this.errorSubject.next('Please upload the required GPX file(s)');
      return;
    }
    this.errorSubject.next(null);
    this.showSettingsModal = false;

    if (this.linkedRace) {
      this.pacingService
        .generateFromRace(
          this.linkedRace.id,
          this.profile,
          this.discipline,
          this.bikeLoops,
          this.runLoops,
        )
        .subscribe({
          next: (plan) => {
            this.setDefaultTab(plan);
          },
          error: (err) => {
            const msg = err.error?.error || err.message || 'Failed to generate pacing plan';
            this.errorSubject.next(msg);
          },
        });
      return;
    }

    this.pacingService
      .generatePacingPlan(
        this.gpxFile,
        this.bikeGpxFile,
        this.runGpxFile,
        this.profile,
        this.discipline,
        this.bikeLoops,
        this.runLoops,
      )
      .subscribe({
        next: (plan) => {
          this.setDefaultTab(plan);
        },
        error: (err) => {
          const msg = err.error?.error || err.message || 'Failed to generate pacing plan';
          this.errorSubject.next(msg);
        },
      });
  }

  clearPlan(): void {
    this.pacingService.clearPlan();
    this.gpxFile = null;
    this.gpxFileName = '';
    this.bikeGpxFile = null;
    this.bikeGpxFileName = '';
    this.runGpxFile = null;
    this.runGpxFileName = '';
    this.bikeLoops = 1;
    this.runLoops = 1;
  }

  private setDefaultTab(plan: PacingPlanResponse): void {
    const tabs = this.getAvailableTabs(plan);
    // Default to first non-SWIM tab (BIKE or RUN), fall back to SWIM
    const nonSwim = tabs.find((t) => t !== 'SWIM');
    this.activeTab = nonSwim || tabs[0] || 'BIKE';
  }
}
