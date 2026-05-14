import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal,
  Signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs/operators';
import {
  CountryFacet,
  DistanceCategory,
  PageResponse,
  Race,
  RaceService,
  SportFacet,
} from '../../../services/race.service';
import { RaceGoalService, RaceGoalSport } from '../../../services/race-goal.service';
import { AuthService } from '../../../services/auth.service';
import {
  applyRaceFilters,
  buildEditFormFromRace,
  canSimulate,
  countryFlag,
  daysUntil,
  DatePreset,
  DISTANCE_PRESETS_BY_SPORT,
  domDay,
  formatKm,
  getDisciplineDistanceKm,
  getGpxDisciplines,
  getLoopCount,
  gpxCount,
  gpxRingDashArray,
  gpxTotal,
  hasGpx,
  isPast,
  missingGpxDisciplines,
  monthAbbr,
  RacesDistancePreset,
  replaceRaceInPage,
  shortYear,
  totalDistanceKm,
  VerifiedFilter,
} from './races-page.utils';

interface SportOption {
  id: string;
  label: string;
  count: number;
}

@Component({
  selector: 'app-races-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RouterLink],
  templateUrl: './races-page.component.html',
  styleUrl: './races-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RacesPageComponent implements OnInit {
  private raceService = inject(RaceService);
  private raceGoalService = inject(RaceGoalService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);

  // ───── Signals ─────
  readonly sportFacets = signal<SportFacet[]>([]);
  readonly countryFacets = signal<CountryFacet[]>([]);
  readonly browseResults = signal<PageResponse<Race> | null>(null);

  readonly selectedSport = signal<string | null>(null);
  readonly selectedCountry = signal<string | null>(null);
  readonly distanceFilters = signal<ReadonlySet<DistanceCategory>>(new Set());
  readonly searchQuery = signal<string>('');
  readonly datePreset = signal<DatePreset>('12m');
  readonly verifiedFilter = signal<VerifiedFilter>('all');
  readonly browsePage = signal<number>(0);
  readonly browseLoading = signal<boolean>(false);

  readonly selectedRaceId = signal<string | null>(null);
  readonly raceCache = signal<Record<string, Race>>({});

  readonly gpxUploading = signal<Record<string, boolean>>({});

  readonly isCreatingRace = signal<boolean>(false);
  readonly isAiCompleting = signal<boolean>(false);
  readonly newRaceTitle = signal<string>('');

  readonly editingRaceId = signal<string | null>(null);
  readonly editForm = signal<Partial<Race>>({});
  readonly isSavingEdit = signal<boolean>(false);

  // Goals — observable bridged to signal
  readonly addedRaceIds: Signal<ReadonlySet<string>> = toSignal(
    this.raceGoalService.goals$.pipe(
      map(
        (goals) =>
          new Set(goals.filter((g) => g.raceId).map((g) => g.raceId!)) as ReadonlySet<string>,
      ),
    ),
    { initialValue: new Set<string>() as ReadonlySet<string> },
  );

  readonly distancePresetsBySport = DISTANCE_PRESETS_BY_SPORT;

  // ───── Computed ─────
  readonly distancePresets = computed<RacesDistancePreset[]>(
    () => DISTANCE_PRESETS_BY_SPORT[this.selectedSport() ?? ''] ?? [],
  );

  readonly sportFacetsView = computed<SportOption[]>(() =>
    this.sportFacets().map((f) => ({
      id: f.sport,
      label: this.translate.instant('RACES.SPORT_' + f.sport.toUpperCase()),
      count: f.raceCount,
    })),
  );

  readonly filteredRaces = computed<Race[]>(() => {
    const results = this.browseResults();
    if (!results) return [];
    const cache = this.raceCache();
    const filtered = applyRaceFilters(results.content, {
      query: this.searchQuery().trim().toLowerCase(),
      distances: this.distanceFilters(),
      verified: this.verifiedFilter(),
      datePreset: this.datePreset(),
    });
    return filtered
      .map((r) => cache[r.id] ?? r)
      .sort((a, b) => a.scheduledDate!.localeCompare(b.scheduledDate!));
  });

  readonly selectedRace = computed<Race | null>(() => {
    const id = this.selectedRaceId();
    if (!id) return null;
    const cache = this.raceCache();
    if (cache[id]) return cache[id];
    const results = this.browseResults();
    if (!results) return null;
    return results.content.find((r) => r.id === id) ?? null;
  });

  ngOnInit(): void {
    this.loadSportFacets();
  }

  // ───── Loading ─────

  loadSportFacets(): void {
    this.raceService
      .getSportFacets()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (facets) => {
          this.sportFacets.set(facets);
          if (!this.selectedSport() && facets.length > 0) {
            this.selectSport(facets[0].sport);
          }
        },
      });
  }

  selectSport(sport: string): void {
    if (this.selectedSport() === sport) return;
    this.selectedSport.set(sport);
    this.selectedCountry.set(null);
    this.countryFacets.set([]);
    this.browseResults.set(null);
    this.distanceFilters.set(new Set());
    this.browsePage.set(0);
    this.browseLoading.set(true);

    this.raceService
      .getCountryFacets(sport)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (facets) => {
          this.countryFacets.set(facets);
          if (facets.length > 0) {
            this.selectCountry(facets[0].country);
          } else {
            this.browseLoading.set(false);
          }
        },
        error: () => this.browseLoading.set(false),
      });
  }

  selectCountry(country: string): void {
    this.selectedCountry.set(country);
    this.browsePage.set(0);
    this.loadBrowsePage();
  }

  loadBrowsePage(): void {
    const sport = this.selectedSport();
    const country = this.selectedCountry();
    if (!sport || !country) return;
    this.browseLoading.set(true);

    this.raceService
      .browseRaces(sport, country, this.browsePage(), 50)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.browseResults.set(page);
          this.browseLoading.set(false);
          // Auto-select first race if current selection no longer in list
          const list = this.filteredRaces();
          const currentId = this.selectedRaceId();
          if (list.length > 0 && (!currentId || !list.some((r) => r.id === currentId))) {
            this.selectedRaceId.set(list[0].id);
          }
        },
        error: () => this.browseLoading.set(false),
      });
  }

  loadMore(): void {
    const r = this.browseResults();
    if (!r || this.browsePage() >= r.totalPages - 1) return;
    this.browsePage.update((p) => p + 1);
    this.loadBrowsePage();
  }

  // ───── Filtering ─────

  toggleDistance(category: DistanceCategory): void {
    this.distanceFilters.update((s) => {
      const next = new Set(s);
      if (next.has(category)) next.delete(category);
      else next.add(category);
      return next;
    });
  }

  resetFilters(): void {
    this.distanceFilters.set(new Set());
    this.searchQuery.set('');
    this.datePreset.set('12m');
    this.verifiedFilter.set('all');
  }

  setDatePreset(preset: DatePreset): void {
    this.datePreset.set(preset);
  }

  setVerifiedFilter(value: VerifiedFilter): void {
    this.verifiedFilter.set(value);
  }

  onSearchChange(value: string): void {
    this.searchQuery.set(value);
  }

  selectRace(race: Race): void {
    this.selectedRaceId.set(race.id);
    if (this.editingRaceId() && this.editingRaceId() !== race.id) {
      this.cancelEdit();
    }
  }

  // ───── Helpers (pure) ─────

  monthAbbr = monthAbbr;
  domDay = domDay;
  shortYear = shortYear;
  daysUntil = daysUntil;
  isPast = isPast;
  formatKm = formatKm;
  totalDistanceKm = totalDistanceKm;
  countryFlag = countryFlag;
  getGpxDisciplines = getGpxDisciplines;
  hasGpx = hasGpx;
  gpxCount = gpxCount;
  gpxTotal = gpxTotal;
  gpxRingDashArray = gpxRingDashArray;
  getDisciplineDistanceKm = getDisciplineDistanceKm;
  getLoopCount = getLoopCount;

  isUploading(raceId: string, discipline: string): boolean {
    return !!this.gpxUploading()[raceId + '_' + discipline];
  }

  // ───── Actions ─────

  addToMyGoals(race: Race): void {
    this.raceGoalService
      .createGoal({
        raceId: race.id,
        title: race.title,
        sport: race.sport as RaceGoalSport,
        location: race.location,
        distance: race.distance,
        priority: 'A',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => {} });
  }

  canSimulate = canSimulate;

  simulateRace(race: Race): void {
    if (!canSimulate(race)) return;
    this.router.navigate(['/pacing'], { queryParams: { raceId: race.id } });
  }

  getSimulateDisabledReason(race: Race): string {
    const missing = missingGpxDisciplines(race);
    return missing.length === 0
      ? ''
      : this.translate.instant('RACES.SIMULATE_MISSING_GPX', { missing: missing.join(' / ') });
  }

  isOwnRace(race: Race): boolean {
    return !!race.createdBy && race.createdBy === this.authService.currentUser?.id;
  }

  // ───── Create ─────

  toggleCreateRace(): void {
    this.isCreatingRace.update((v) => !v);
    if (!this.isCreatingRace()) this.newRaceTitle.set('');
  }

  setNewRaceTitle(value: string): void {
    this.newRaceTitle.set(value);
  }

  createAndCompleteRace(): void {
    const title = this.newRaceTitle().trim();
    if (!title) return;
    this.isAiCompleting.set(true);

    this.raceService
      .createRace(title)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (race) => {
          this.raceService
            .aiComplete(race.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: () => this.finishCreate(),
              error: () => this.finishCreate(),
            });
        },
        error: () => this.isAiCompleting.set(false),
      });
  }

  private finishCreate(): void {
    this.isAiCompleting.set(false);
    this.isCreatingRace.set(false);
    this.newRaceTitle.set('');
    this.loadSportFacets();
  }

  // ───── Edit ─────

  startEdit(race: Race): void {
    const cache = this.raceCache();
    const r = cache[race.id] ?? race;
    this.editingRaceId.set(race.id);
    this.editForm.set(buildEditFormFromRace(r));
  }

  cancelEdit(): void {
    this.editingRaceId.set(null);
    this.editForm.set({});
  }

  saveEdit(race: Race): void {
    if (!this.editingRaceId()) return;
    this.isSavingEdit.set(true);
    this.raceService
      .updateRace(race.id, this.editForm())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.raceCache.update((c) => ({ ...c, [race.id]: updated }));
          this.browseResults.update((results) =>
            results ? replaceRaceInPage(results, updated) : results,
          );
          this.editingRaceId.set(null);
          this.editForm.set({});
          this.isSavingEdit.set(false);
        },
        error: () => this.isSavingEdit.set(false),
      });
  }

  onEditFieldChange<K extends keyof Race>(field: K, value: Race[K]): void {
    this.editForm.update((f) => ({ ...f, [field]: value }));
  }

  // ───── GPX ─────

  onGpxFileSelected(event: Event, race: Race, discipline: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';
    if (!file.name.toLowerCase().endsWith('.gpx')) return;

    const key = race.id + '_' + discipline;
    const setUploading = (v: boolean) => this.gpxUploading.update((g) => ({ ...g, [key]: v }));
    setUploading(true);

    this.raceService.uploadGpx(race.id, discipline, file).subscribe({
      next: () =>
        this.raceService.getRace(race.id).subscribe({
          next: (updated) => {
            this.raceCache.update((c) => ({ ...c, [race.id]: updated }));
            setUploading(false);
          },
          error: () => setUploading(false),
        }),
      error: () => setUploading(false),
    });
  }

  updateLoops(race: Race, discipline: string, event: Event): void {
    const value = +(event.target as HTMLInputElement).value;
    const loops = Math.max(1, Math.min(99, value || 1));
    const key = (discipline + 'GpxLoops') as 'swimGpxLoops' | 'bikeGpxLoops' | 'runGpxLoops';
    this.raceService
      .updateRace(race.id, { [key]: loops })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => this.raceCache.update((c) => ({ ...c, [race.id]: updated })),
      });
  }
}
