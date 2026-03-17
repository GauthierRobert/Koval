import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {CountryFacet, PageResponse, Race, RaceService, SportFacet,} from '../../../services/race.service';
import {RaceGoalService} from '../../../services/race-goal.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';

@Component({
  selector: 'app-races-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SportIconComponent],
  templateUrl: './races-page.component.html',
  styleUrl: './races-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RacesPageComponent implements OnInit {
  private raceService = inject(RaceService);
  private raceGoalService = inject(RaceGoalService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // Browse state
  sportFacets: SportFacet[] = [];
  countryFacets: CountryFacet[] = [];
  browseResults: PageResponse<Race> | null = null;
  selectedBrowseSport: string | null = null;
  selectedBrowseCountry: string | null = null;
  browsePage = 0;
  browseLoading = false;

  // "Added" tracking for ADD TO MY GOALS buttons
  addedRaceIds = new Set<string>();

  // Expand / GPX state
  expandedRaceId: string | null = null;
  gpxUploading: Record<string, boolean> = {};
  raceCache: Record<string, Race> = {};

  // Create race state
  isCreatingRace = false;
  isAiCompleting = false;
  newRaceTitle = '';

  readonly monthNames = [
    '', 'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  ngOnInit(): void {
    this.loadSportFacets();
    this.raceGoalService.goals$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((goals) => {
      for (const g of goals) {
        if (g.raceId) this.addedRaceIds.add(g.raceId);
      }
      this.cdr.markForCheck();
    });
  }

  loadSportFacets(): void {
    this.raceService.getSportFacets().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (facets) => {
        this.sportFacets = facets;
        this.cdr.markForCheck();
      },
    });
  }

  selectBrowseSport(sport: string): void {
    this.selectedBrowseSport = sport;
    this.selectedBrowseCountry = null;
    this.browseResults = null;
    this.browsePage = 0;
    this.browseLoading = true;
    this.cdr.markForCheck();

    this.raceService.getCountryFacets(sport).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (facets) => {
        this.countryFacets = facets;
        this.browseLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  selectBrowseCountry(country: string): void {
    this.selectedBrowseCountry = country;
    this.browsePage = 0;
    this.loadBrowsePage();
  }

  loadBrowsePage(): void {
    if (!this.selectedBrowseSport || !this.selectedBrowseCountry) return;
    this.browseLoading = true;
    this.cdr.markForCheck();

    this.raceService
      .browseRaces(this.selectedBrowseSport, this.selectedBrowseCountry, this.browsePage)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.browseResults = page;
          this.browseLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  browsePrevPage(): void {
    if (this.browsePage > 0) {
      this.browsePage--;
      this.loadBrowsePage();
    }
  }

  browseNextPage(): void {
    if (this.browseResults && this.browsePage < this.browseResults.totalPages - 1) {
      this.browsePage++;
      this.loadBrowsePage();
    }
  }

  backToSports(): void {
    this.selectedBrowseSport = null;
    this.selectedBrowseCountry = null;
    this.countryFacets = [];
    this.browseResults = null;
    this.browsePage = 0;
    this.cdr.markForCheck();
  }

  backToCountries(): void {
    this.selectedBrowseCountry = null;
    this.browseResults = null;
    this.browsePage = 0;
    this.cdr.markForCheck();
  }

  addToMyGoals(race: Race): void {
    if (this.addedRaceIds.has(race.id)) return;
    this.raceGoalService
      .createGoal({
        raceId: race.id,
        title: race.title,
        sport: race.sport as any,
        location: race.location,
        distance: race.distance,
        priority: 'A',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.addedRaceIds.add(race.id);
          this.cdr.markForCheck();
        },
      });
  }

  toggleCreateRace(): void {
    this.isCreatingRace = !this.isCreatingRace;
    if (!this.isCreatingRace) {
      this.newRaceTitle = '';
    }
    this.cdr.markForCheck();
  }

  createAndCompleteRace(): void {
    if (!this.newRaceTitle.trim()) return;
    this.isAiCompleting = true;
    this.cdr.markForCheck();

    this.raceService.createRace(this.newRaceTitle.trim()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (race) => {
        this.raceService.aiComplete(race.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: () => {
            this.isAiCompleting = false;
            this.isCreatingRace = false;
            this.newRaceTitle = '';
            // Reload sport facets to reflect new race
            this.loadSportFacets();
            this.cdr.markForCheck();
          },
          error: () => {
            this.isAiCompleting = false;
            this.isCreatingRace = false;
            this.newRaceTitle = '';
            this.loadSportFacets();
            this.cdr.markForCheck();
          },
        });
      },
      error: () => {
        this.isAiCompleting = false;
        this.cdr.markForCheck();
      },
    });
  }

  toggleExpand(race: Race): void {
    this.expandedRaceId = this.expandedRaceId === race.id ? null : race.id;
    this.cdr.markForCheck();
  }

  getCachedRace(race: Race): Race {
    return this.raceCache[race.id] ?? race;
  }

  getGpxDisciplines(race: Race): string[] {
    switch (race.sport?.toUpperCase()) {
      case 'TRIATHLON': return ['swim', 'bike', 'run'];
      case 'CYCLING': return ['bike'];
      case 'RUNNING': return ['run'];
      case 'SWIMMING': return ['swim'];
      default: return ['bike'];
    }
  }

  hasGpxForDiscipline(race: Race, discipline: string): boolean {
    switch (discipline) {
      case 'swim': return !!race.hasSwimGpx;
      case 'bike': return !!race.hasBikeGpx;
      case 'run': return !!race.hasRunGpx;
      default: return false;
    }
  }

  isGpxUploading(raceId: string, discipline: string): boolean {
    return !!this.gpxUploading[raceId + '_' + discipline];
  }

  onGpxFileSelected(event: Event, race: Race, discipline: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';
    this.uploadGpx(race, discipline, file);
  }

  uploadGpx(race: Race, discipline: string, file: File): void {
    if (!file.name.toLowerCase().endsWith('.gpx')) return;
    const key = race.id + '_' + discipline;
    this.gpxUploading[key] = true;
    this.cdr.markForCheck();

    this.raceService.uploadGpx(race.id, discipline, file).subscribe({
      next: () => {
        this.raceService.getRace(race.id).subscribe({
          next: (updated) => {
            this.raceCache[race.id] = updated;
            this.gpxUploading[key] = false;
            this.cdr.markForCheck();
          },
          error: () => {
            this.gpxUploading[key] = false;
            this.cdr.markForCheck();
          },
        });
      },
      error: () => {
        this.gpxUploading[key] = false;
        this.cdr.markForCheck();
      },
    });
  }

  canSimulate(race: Race): boolean {
    switch (race.sport?.toUpperCase()) {
      case 'CYCLING': return !!race.hasBikeGpx;
      case 'RUNNING': return !!race.hasRunGpx;
      case 'TRIATHLON': return !!race.hasBikeGpx && !!race.hasRunGpx;
      case 'SWIMMING': return true;
      default: return !!race.hasBikeGpx;
    }
  }

  getSimulateDisabledReason(race: Race): string {
    const missing: string[] = [];
    const sport = race.sport?.toUpperCase();
    if (sport === 'CYCLING' || sport === 'TRIATHLON' || (!sport || !['RUNNING', 'SWIMMING'].includes(sport))) {
      if (!race.hasBikeGpx) missing.push('Bike');
    }
    if (sport === 'RUNNING' || sport === 'TRIATHLON') {
      if (!race.hasRunGpx) missing.push('Run');
    }
    if (missing.length === 0) return '';
    return 'Upload ' + missing.join(' and ') + ' GPX to simulate';
  }

  simulateRace(race: Race): void {
    if (!this.canSimulate(this.getCachedRace(race))) return;
    this.router.navigate(['/pacing'], { queryParams: { raceId: race.id } });
  }

  formatDistance(meters?: number): string {
    if (!meters) return '';
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }
}
