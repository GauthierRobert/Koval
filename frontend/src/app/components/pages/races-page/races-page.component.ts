import {ChangeDetectionStrategy, Component, DestroyRef, computed, inject, OnInit, signal, Signal} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Router} from '@angular/router';
import {map} from 'rxjs/operators';
import {CountryFacet, DistanceCategory, PageResponse, Race, RaceService, SportFacet} from '../../../services/race.service';
import {RaceGoalService} from '../../../services/race-goal.service';
import {AuthService} from '../../../services/auth.service';

interface SportOption {
  id: string;
  label: string;
  count: number;
}

type DatePreset = '12m' | 'y2026' | 'y2027';
type VerifiedFilter = 'all' | 'verified' | 'community';

@Component({
  selector: 'app-races-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
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
      map(goals => new Set(goals.filter(g => g.raceId).map(g => g.raceId!)) as ReadonlySet<string>),
    ),
    {initialValue: new Set<string>() as ReadonlySet<string>},
  );

  // Distance presets per sport — value is the structured DistanceCategory used
  // for exact-match filtering; label is what the user sees on the chip.
  readonly distancePresetsBySport: Record<string, { value: DistanceCategory; label: string }[]> = {
    TRIATHLON: [
      { value: 'TRI_PROMO', label: 'Promo' },
      { value: 'TRI_SUPER_SPRINT', label: 'Super Sprint' },
      { value: 'TRI_SPRINT', label: 'Sprint' },
      { value: 'TRI_OLYMPIC', label: 'Olympic' },
      { value: 'TRI_HALF', label: '70.3' },
      { value: 'TRI_IRONMAN', label: 'Ironman' },
      { value: 'TRI_ULTRA', label: 'Ultra' },
      { value: 'TRI_AQUATHLON', label: 'Aquathlon' },
      { value: 'TRI_DUATHLON', label: 'Duathlon' },
      { value: 'TRI_AQUABIKE', label: 'Aquabike' },
      { value: 'TRI_CROSS', label: 'Cross / XTERRA' },
    ],
    RUNNING: [
      { value: 'RUN_5K', label: '5K' },
      { value: 'RUN_10K', label: '10K' },
      { value: 'RUN_HALF_MARATHON', label: 'Semi' },
      { value: 'RUN_MARATHON', label: 'Marathon' },
      { value: 'RUN_ULTRA', label: 'Ultra' },
    ],
    CYCLING: [
      { value: 'BIKE_GRAN_FONDO', label: 'Gran Fondo' },
      { value: 'BIKE_MEDIO_FONDO', label: 'Medio' },
      { value: 'BIKE_TT', label: 'TT' },
      { value: 'BIKE_ULTRA', label: 'Ultra' },
    ],
    SWIMMING: [
      { value: 'SWIM_1500M', label: '1.5K' },
      { value: 'SWIM_5K', label: '5K' },
      { value: 'SWIM_10K', label: '10K' },
      { value: 'SWIM_MARATHON', label: 'Marathon' },
      { value: 'SWIM_ULTRA', label: 'Ultra' },
    ],
    OTHER: [],
  };

  // ───── Computed ─────
  readonly distancePresets = computed<{ value: DistanceCategory; label: string }[]>(
    () => this.distancePresetsBySport[this.selectedSport() ?? ''] ?? [],
  );

  readonly sportFacetsView = computed<SportOption[]>(() =>
    this.sportFacets().map(f => ({
      id: f.sport,
      label: this.translate.instant('RACES.SPORT_' + f.sport.toUpperCase()),
      count: f.raceCount,
    })),
  );

  readonly filteredRaces = computed<Race[]>(() => {
    const results = this.browseResults();
    if (!results) return [];
    const q = this.searchQuery().trim().toLowerCase();
    const dists = this.distanceFilters();
    const verified = this.verifiedFilter();
    const preset = this.datePreset();
    const cache = this.raceCache();

    const today = new Date(); today.setHours(0, 0, 0, 0);
    const in12Months = new Date(today); in12Months.setMonth(in12Months.getMonth() + 12);

    return results.content.filter(r => {
      if (dists.size > 0) {
        // Exact match against the structured DistanceCategory enum — no more
        // substring matching on the free-form display string.
        if (!r.distanceCategory || !dists.has(r.distanceCategory)) return false;
      }
      if (verified === 'verified' && !r.verified) return false;
      if (verified === 'community' && r.verified) return false;
      if (q) {
        const haystack = [r.title, r.location, r.country, r.distance].join(' ').toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      if (!r.scheduledDate) return false;
      if (preset === '12m') {
        const t = new Date(r.scheduledDate + 'T00:00:00').getTime();
        if (t < today.getTime() || t > in12Months.getTime()) return false;
      } else if (preset === 'y2026') {
        if (r.scheduledDate.slice(0, 4) !== '2026') return false;
      } else if (preset === 'y2027') {
        if (r.scheduledDate.slice(0, 4) !== '2027') return false;
      }
      return true;
    }).map(r => cache[r.id] ?? r);
  });

  readonly selectedRace = computed<Race | null>(() => {
    const id = this.selectedRaceId();
    if (!id) return null;
    const cache = this.raceCache();
    if (cache[id]) return cache[id];
    const results = this.browseResults();
    if (!results) return null;
    return results.content.find(r => r.id === id) ?? null;
  });

  ngOnInit(): void {
    this.loadSportFacets();
  }

  // ───── Loading ─────

  loadSportFacets(): void {
    this.raceService.getSportFacets().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

    this.raceService.getCountryFacets(sport).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
          if (list.length > 0 && (!currentId || !list.some(r => r.id === currentId))) {
            this.selectedRaceId.set(list[0].id);
          }
        },
        error: () => this.browseLoading.set(false),
      });
  }

  loadMore(): void {
    const r = this.browseResults();
    if (!r || this.browsePage() >= r.totalPages - 1) return;
    this.browsePage.update(p => p + 1);
    this.loadBrowsePage();
  }

  // ───── Filtering ─────

  toggleDistance(category: DistanceCategory): void {
    this.distanceFilters.update(s => {
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

  setDatePreset(preset: DatePreset): void { this.datePreset.set(preset); }

  setVerifiedFilter(value: VerifiedFilter): void { this.verifiedFilter.set(value); }

  onSearchChange(value: string): void { this.searchQuery.set(value); }

  selectRace(race: Race): void {
    this.selectedRaceId.set(race.id);
    if (this.editingRaceId() && this.editingRaceId() !== race.id) {
      this.cancelEdit();
    }
  }

  // ───── Helpers (pure) ─────

  monthAbbr(iso?: string): string {
    if (!iso || iso.length < 7) return '—';
    const map: Record<string, string> = {
      '01': 'JAN', '02': 'FEV', '03': 'MAR', '04': 'AVR', '05': 'MAI', '06': 'JUN',
      '07': 'JUL', '08': 'AOU', '09': 'SEP', '10': 'OCT', '11': 'NOV', '12': 'DEC',
    };
    return map[iso.slice(5, 7)] ?? '—';
  }

  domDay(iso?: string): string {
    if (!iso || iso.length < 10) return '–';
    return parseInt(iso.slice(8, 10), 10).toString();
  }

  shortYear(iso?: string): string {
    if (!iso || iso.length < 4) return '';
    return iso.slice(2, 4);
  }

  daysUntil(iso?: string): number | null {
    if (!iso) return null;
    const target = new Date(iso + 'T00:00:00').getTime();
    const today = new Date(); today.setHours(0, 0, 0, 0);
    return Math.round((target - today.getTime()) / (1000 * 60 * 60 * 24));
  }

  isPast(race: Race): boolean {
    const d = this.daysUntil(race.scheduledDate);
    return d !== null && d < 0;
  }

  formatKm(meters?: number): string {
    if (!meters) return '';
    return (meters / 1000).toFixed(1);
  }

  totalDistanceKm(race: Race): string {
    const total = ((race.swimDistanceM ?? 0) + (race.bikeDistanceM ?? 0) + (race.runDistanceM ?? 0)) / 1000;
    return total > 0 ? total.toFixed(1) : '—';
  }

  countryFlag(code?: string): string {
    if (!code || code.length !== 2) return '';
    const A = 0x1F1E6;
    return String.fromCodePoint(A + code.toUpperCase().charCodeAt(0) - 65) +
      String.fromCodePoint(A + code.toUpperCase().charCodeAt(1) - 65);
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

  hasGpx(race: Race, discipline: string): boolean {
    switch (discipline) {
      case 'swim': return !!race.hasSwimGpx;
      case 'bike': return !!race.hasBikeGpx;
      case 'run': return !!race.hasRunGpx;
      default: return false;
    }
  }

  gpxCount(race: Race): number {
    return this.getGpxDisciplines(race).filter(d => this.hasGpx(race, d)).length;
  }

  gpxTotal(race: Race): number {
    return this.getGpxDisciplines(race).length;
  }

  gpxRingDashArray(race: Race, circumference: number): string {
    const pct = this.gpxCount(race) / this.gpxTotal(race);
    return `${circumference * pct} ${circumference}`;
  }

  getDisciplineDistanceKm(race: Race, discipline: string): number | null {
    const m = discipline === 'swim' ? race.swimDistanceM
      : discipline === 'bike' ? race.bikeDistanceM
        : discipline === 'run' ? race.runDistanceM : null;
    return m ? m / 1000 : null;
  }

  getLoopCount(race: Race, discipline: string): number {
    switch (discipline) {
      case 'swim': return race.swimGpxLoops ?? 1;
      case 'bike': return race.bikeGpxLoops ?? 1;
      case 'run': return race.runGpxLoops ?? 1;
      default: return 1;
    }
  }

  isUploading(raceId: string, discipline: string): boolean {
    return !!this.gpxUploading()[raceId + '_' + discipline];
  }

  // ───── Actions ─────

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
      .subscribe({error: () => {}});
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

  simulateRace(race: Race): void {
    if (!this.canSimulate(race)) return;
    this.router.navigate(['/pacing'], {queryParams: {raceId: race.id}});
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
    return this.translate.instant('RACES.SIMULATE_MISSING_GPX', {missing: missing.join(' / ')});
  }

  isOwnRace(race: Race): boolean {
    return !!race.createdBy && race.createdBy === this.authService.currentUser?.id;
  }

  // ───── Create ─────

  toggleCreateRace(): void {
    this.isCreatingRace.update(v => !v);
    if (!this.isCreatingRace()) this.newRaceTitle.set('');
  }

  setNewRaceTitle(value: string): void { this.newRaceTitle.set(value); }

  createAndCompleteRace(): void {
    const title = this.newRaceTitle().trim();
    if (!title) return;
    this.isAiCompleting.set(true);

    this.raceService.createRace(title).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (race) => {
        this.raceService.aiComplete(race.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.editForm.set({
      title: r.title,
      sport: r.sport,
      location: r.location ?? '',
      country: r.country ?? '',
      distance: r.distance ?? '',
      distanceCategory: r.distanceCategory,
      swimDistanceM: r.swimDistanceM,
      bikeDistanceM: r.bikeDistanceM,
      runDistanceM: r.runDistanceM,
      elevationGainM: r.elevationGainM,
      description: r.description ?? '',
      website: r.website ?? '',
      scheduledDate: r.scheduledDate ?? '',
    });
  }

  cancelEdit(): void {
    this.editingRaceId.set(null);
    this.editForm.set({});
  }

  saveEdit(race: Race): void {
    if (!this.editingRaceId()) return;
    this.isSavingEdit.set(true);

    this.raceService.updateRace(race.id, this.editForm()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.raceCache.update(c => ({...c, [race.id]: updated}));
        this.browseResults.update(results => {
          if (!results) return results;
          const idx = results.content.findIndex(r => r.id === race.id);
          if (idx === -1) return results;
          const content = [...results.content];
          content[idx] = updated;
          return {...results, content};
        });
        this.editingRaceId.set(null);
        this.editForm.set({});
        this.isSavingEdit.set(false);
      },
      error: () => this.isSavingEdit.set(false),
    });
  }

  onEditFieldChange(field: keyof Race, value: any): void {
    this.editForm.update(f => ({...f, [field]: value}));
  }

  // ───── GPX ─────

  onGpxFileSelected(event: Event, race: Race, discipline: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';
    if (!file.name.toLowerCase().endsWith('.gpx')) return;

    const key = race.id + '_' + discipline;
    this.gpxUploading.update(g => ({...g, [key]: true}));

    this.raceService.uploadGpx(race.id, discipline, file).subscribe({
      next: () => {
        this.raceService.getRace(race.id).subscribe({
          next: (updated) => {
            this.raceCache.update(c => ({...c, [race.id]: updated}));
            this.gpxUploading.update(g => ({...g, [key]: false}));
          },
          error: () => this.gpxUploading.update(g => ({...g, [key]: false})),
        });
      },
      error: () => this.gpxUploading.update(g => ({...g, [key]: false})),
    });
  }

  updateLoops(race: Race, discipline: string, event: Event): void {
    const value = +(event.target as HTMLInputElement).value;
    const loops = Math.max(1, Math.min(99, value || 1));
    const key = (discipline + 'GpxLoops') as 'swimGpxLoops' | 'bikeGpxLoops' | 'runGpxLoops';
    this.raceService.updateRace(race.id, {[key]: loops}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => this.raceCache.update(c => ({...c, [race.id]: updated})),
    });
  }
}
