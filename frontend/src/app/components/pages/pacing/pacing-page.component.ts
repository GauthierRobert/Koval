import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  AthleteProfile,
  PacingPlanResponse,
  PacingSegment,
  PacingService,
  RouteCoordinate,
  SegmentRange,
} from '../../../services/pacing.service';
import {AuthService} from '../../../services/auth.service';
import {Race, RaceService, SimulationRequest} from '../../../services/race.service';
import {ElevationChartComponent} from './elevation-chart/elevation-chart.component';
import {RouteMapComponent} from './route-map/route-map.component';
import {GpxUploadZoneComponent} from './gpx-upload-zone/gpx-upload-zone.component';
import {DisciplineSelectorComponent} from './discipline-selector/discipline-selector.component';
import {PacingParameterBarComponent} from './pacing-parameter-bar/pacing-parameter-bar.component';
import {PacingSummaryCardsComponent} from './pacing-summary-cards/pacing-summary-cards.component';
import {PacingSettingsModalComponent} from './pacing-settings-modal/pacing-settings-modal.component';
import {BehaviorSubject, Observable} from 'rxjs';
import {
  groupingCacheKey,
  groupPacingSegments,
  RacePlanGroup,
} from './pacing-plan-grouping';
import {
  activeRouteCoordinates,
  activeSegments,
  availableTabsForPlan,
  disciplineFromRaceSport,
  formatPacingDistance,
  formatPacingTime,
  generateBlockedReason,
  terrainLabel,
} from './pacing-page.helpers';

@Component({
  selector: 'app-pacing-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    ElevationChartComponent,
    RouteMapComponent,
    GpxUploadZoneComponent,
    DisciplineSelectorComponent,
    PacingParameterBarComponent,
    PacingSummaryCardsComponent,
    PacingSettingsModalComponent,
  ],
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
  private translate = inject(TranslateService);

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

  discipline = 'TRIATHLON';
  profile: AthleteProfile = {
    // TODO: Re-enable fatigue resistance once backend integrates it
    fatigueResistance: 0.5,
    nutritionPreference: 'MIXED',
    bikeType: 'ROAD_AERO',
  };

  // Percentage-based target inputs
  targetPowerPct: number | null = null;
  targetPacePct: number | null = null;

  activeTab = 'BIKE';
  bikeShowSpeed = false;
  showGroupMean = false;
  groupBandW = 5;
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
    const key = groupingCacheKey(segments, this.groupBandW);
    if (this.groupedPlanCache?.key === key) {
      return this.groupedPlanCache.result;
    }
    const result = groupPacingSegments(segments, this.groupBandW, (g) => this.terrainLabel(g));
    this.groupedPlanCache = {key, result};
    return result;
  }

  private terrainLabel(gradient: number): string {
    return terrainLabel(gradient, this.translate);
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

    this.route.queryParams.subscribe((params) => {
      const raceId = params['raceId'];
      if (!raceId) return;
      this.linkedGoalId = params['goalId'] ?? null;
      this.raceService.getRace(raceId).subscribe({
        next: (race) => {
          this.linkedRace = race;
          this.discipline = disciplineFromRaceSport(race.sport) ?? this.discipline;
          if (race.swimDistanceM) this.profile.swimDistanceM = race.swimDistanceM;
          this.cdr.markForCheck();
        },
      });
    });
  }

  onGpxFileSelected(file: File): void {
    this.setFile(file, 'gpx');
  }

  onBikeGpxFileSelected(file: File): void {
    this.setFile(file, 'bike');
  }

  onRunGpxFileSelected(file: File): void {
    this.setFile(file, 'run');
  }

  onDisciplineChange(value: string): void {
    this.discipline = value;
  }

  onBikeTypeChange(value: string): void {
    this.profile.bikeType = value as 'TT' | 'ROAD_AERO' | 'ROAD';
  }

  private setFile(file: File, target: 'gpx' | 'bike' | 'run' = 'gpx'): void {
    if (!file.name.toLowerCase().endsWith('.gpx')) {
      this.errorSubject.next(this.translate.instant('PACING.ERROR_INVALID_GPX'));
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
    return !this.getGenerateBlockedReason();
  }

  getGenerateBlockedReason(): string | null {
    return generateBlockedReason({
      discipline: this.discipline,
      linkedRace: this.linkedRace,
      bikeGpxFile: this.bikeGpxFile,
      runGpxFile: this.runGpxFile,
      gpxFile: this.gpxFile,
    }, this.translate);
  }

  generate(): void {
    this.runPlanGeneration({saveRequest: true});
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

  getAvailableTabs(plan: PacingPlanResponse): string[] { return availableTabsForPlan(plan); }
  getActiveSegments(plan: PacingPlanResponse): PacingSegment[] { return activeSegments(plan, this.activeTab); }
  getActiveRouteCoordinates(plan: PacingPlanResponse): RouteCoordinate[] | null {
    return activeRouteCoordinates(plan, this.activeTab);
  }

  formatTime(seconds: number): string { return formatPacingTime(seconds); }
  formatDistance(meters: number): string { return formatPacingDistance(meters); }

  closeSettingsModal(): void {
    this.showSettingsModal = false;
  }

  regenerate(): void {
    this.showSettingsModal = false;
    this.runPlanGeneration({saveRequest: false});
  }

  private runPlanGeneration(opts: {saveRequest: boolean}): void {
    if (!this.canGenerate()) {
      this.errorSubject.next(this.translate.instant('PACING.ERROR_UPLOAD_REQUIRED_FILES'));
      return;
    }
    this.errorSubject.next(null);

    const source$: Observable<PacingPlanResponse> = this.linkedRace
      ? this.pacingService.generateFromRace(
          this.linkedRace.id,
          this.profile,
          this.discipline,
          this.bikeLoops,
          this.runLoops,
        )
      : this.pacingService.generatePacingPlan(
          this.gpxFile,
          this.bikeGpxFile,
          this.runGpxFile,
          this.profile,
          this.discipline,
          this.bikeLoops,
          this.runLoops,
        );

    source$.subscribe({
      next: (plan) => {
        this.setDefaultTab(plan);
        if (opts.saveRequest && this.linkedRace) this.saveSimulationRequest();
      },
      error: (err) => {
        const msg = err.error?.error || err.message || this.translate.instant('PACING.ERROR_FAILED_GENERATE');
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

  getGroupMeanPowers(segments: PacingSegment[]): number[] | null {
    if (!this.showGroupMean) return null;
    const groups = this.getGroupedPlan(segments);
    const result = new Array<number>(segments.length);
    for (const group of groups) {
      for (let i = group.segmentStart; i <= group.segmentEnd; i++) {
        result[i] = group.targetPower ?? 0;
      }
    }
    return result;
  }

  private setDefaultTab(plan: PacingPlanResponse): void {
    const tabs = this.getAvailableTabs(plan);
    // Default to first non-SWIM tab (BIKE or RUN), fall back to SWIM
    const nonSwim = tabs.find((t) => t !== 'SWIM');
    this.activeTab = nonSwim || tabs[0] || 'BIKE';
  }
}
