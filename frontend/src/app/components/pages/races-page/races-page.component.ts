import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Router} from '@angular/router';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {CountryFacet, PageResponse, Race, RaceService, SportFacet,} from '../../../services/race.service';
import {RaceGoalService} from '../../../services/race-goal.service';
import {AuthService} from '../../../services/auth.service';
import {RaceSummaryCardComponent} from '../../shared/race-summary-card/race-summary-card.component';

@Component({
  selector: 'app-races-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RaceSummaryCardComponent],
  templateUrl: './races-page.component.html',
  styleUrl: './races-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RacesPageComponent implements OnInit {
  private raceService = inject(RaceService);
  private raceGoalService = inject(RaceGoalService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);

  // Browse state
  sportFacets: SportFacet[] = [];
  countryFacets: CountryFacet[] = [];
  browseResults: PageResponse<Race> | null = null;
  selectedBrowseSport: string | null = null;
  selectedBrowseCountry: string | null = null;
  browsePage = 0;
  browseLoading = false;

  // "Added" tracking for ADD TO MY GOALS buttons
  addedRaceIds$: Observable<Set<string>> = this.raceGoalService.goals$.pipe(
    map(goals => new Set(goals.filter(g => g.raceId).map(g => g.raceId!)))
  );

  // Expand / GPX state
  expandedRaceId: string | null = null;
  gpxUploading: Record<string, boolean> = {};
  raceCache: Record<string, Race> = {};

  // Create race state
  isCreatingRace = false;
  isAiCompleting = false;
  newRaceTitle = '';

  // Edit race state
  editingRaceId: string | null = null;
  editForm: Partial<Race> = {};
  isSavingEdit = false;

  ngOnInit(): void {
    this.loadSportFacets();
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
        error: () => {},
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
    return this.translate.instant('RACES.SIMULATE_MISSING_GPX', { missing: missing.join(' and ') });
  }

  simulateRace(race: Race): void {
    if (!this.canSimulate(this.getCachedRace(race))) return;
    this.router.navigate(['/pacing'], { queryParams: { raceId: race.id } });
  }

  isOwnRace(race: Race): boolean {
    return !!race.createdBy && race.createdBy === this.authService.currentUser?.id;
  }

  startEdit(race: Race): void {
    this.editingRaceId = race.id;
    this.expandedRaceId = race.id;
    const r = this.getCachedRace(race);
    this.editForm = {
      title: r.title,
      sport: r.sport,
      location: r.location ?? '',
      country: r.country ?? '',
      distance: r.distance ?? '',
      swimDistanceM: r.swimDistanceM,
      bikeDistanceM: r.bikeDistanceM,
      runDistanceM: r.runDistanceM,
      elevationGainM: r.elevationGainM,
      description: r.description ?? '',
      website: r.website ?? '',
      scheduledDate: r.scheduledDate ?? '',
    };
    this.cdr.markForCheck();
  }

  cancelEdit(): void {
    this.editingRaceId = null;
    this.editForm = {};
    this.cdr.markForCheck();
  }

  saveEdit(race: Race): void {
    if (!this.editingRaceId) return;
    this.isSavingEdit = true;
    this.cdr.markForCheck();

    this.raceService.updateRace(race.id, this.editForm).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.raceCache[race.id] = updated;
        if (this.browseResults) {
          const idx = this.browseResults.content.findIndex(r => r.id === race.id);
          if (idx !== -1) this.browseResults.content[idx] = updated;
        }
        this.editingRaceId = null;
        this.editForm = {};
        this.isSavingEdit = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isSavingEdit = false;
        this.cdr.markForCheck();
      },
    });
  }

  updateLoops(race: Race, discipline: string, event: Event): void {
    const value = +(event.target as HTMLInputElement).value;
    const loops = Math.max(1, Math.min(99, value || 1));
    const key = discipline + 'GpxLoops' as 'swimGpxLoops' | 'bikeGpxLoops' | 'runGpxLoops';
    this.raceService.updateRace(race.id, { [key]: loops }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.raceCache[race.id] = updated;
        this.cdr.markForCheck();
      },
    });
  }

  onEditFormChange(form: Partial<Race>): void {
    this.editForm = form;
    this.cdr.markForCheck();
  }

  onGpxFileFromCard(ev: {race: Race; discipline: string; event: Event}): void {
    this.onGpxFileSelected(ev.event, ev.race, ev.discipline);
  }

  onLoopsFromCard(ev: {race: Race; discipline: string; event: Event}): void {
    this.updateLoops(ev.race, ev.discipline, ev.event);
  }

  formatDistance(meters?: number): string {
    if (!meters) return '';
    if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
    return Math.round(meters) + ' m';
  }
}
