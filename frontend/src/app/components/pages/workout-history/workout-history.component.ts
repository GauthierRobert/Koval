import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, take } from 'rxjs/operators';
import { ResponsiveService } from '../../../services/responsive.service';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import { SessionAnalysisComponent } from '../session-analysis/session-analysis.component';
import { FilterPillsComponent } from '../../shared/filter-pills/filter-pills.component';
import { ModalShellComponent } from '../../shared/modal-shell/modal-shell.component';
import { SkeletonComponent } from '../../shared/skeleton/skeleton.component';
import { HistoryService, SavedSession, SessionFilters } from '../../../services/history.service';
import {
  alignmentZone,
  AlignmentZone,
  effectiveAlignmentScore,
} from '../../../models/alignment.model';
import { formatTimeText } from '../../shared/format/format.utils';

import { FitExportService } from '../../../services/fit-export.service';
import { CsvExportService } from '../../../services/csv-export.service';
import { AuthService } from '../../../services/auth.service';
import { MetricsService } from '../../../services/metrics.service';
import { StravaSyncService } from '../../../services/strava-sync.service';
import {
  buildHistoryRows,
  formatWeekRange,
  groupTitleFor,
  HistoryRow,
  SessionGroup,
  weekKeyOf,
} from './workout-history-grouping';
import { parseFitToSession } from './workout-history-fit-parser';
import {
  ManualSessionInput,
  ManualSessionModalComponent,
} from './manual-session-modal/manual-session-modal.component';
import {
  LinkChange,
  LinkSessionsModalComponent,
} from './link-sessions-modal/link-sessions-modal.component';
import { LinkToScheduleModalComponent } from './link-to-schedule-modal/link-to-schedule-modal.component';
import { SessionActionKind } from '../session-analysis/session-action-panel/session-action-panel.component';
import { goalDate, RaceGoalService } from '../../../services/race-goal.service';
import { forkJoin, of } from 'rxjs';

export type { HistoryRow } from './workout-history-grouping';

type SportFilter = string | null;

/** Local YYYY-MM-DD key — matches the date format used by Race.scheduledDate. */
function toIsoDate(d: Date | string): string {
  const date = new Date(d);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

@Component({
  selector: 'app-workout-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    SportIconComponent,
    SessionAnalysisComponent,
    FilterPillsComponent,
    ModalShellComponent,
    SkeletonComponent,
    ManualSessionModalComponent,
    LinkSessionsModalComponent,
    LinkToScheduleModalComponent,
  ],
  templateUrl: './workout-history.component.html',
  styleUrl: './workout-history.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutHistoryComponent implements OnInit {
  @ViewChild('fitInput') fitInputRef!: ElementRef<HTMLInputElement>;

  historyService = inject(HistoryService);
  private fitExport = inject(FitExportService);
  private csvExport = inject(CsvExportService);
  private translate = inject(TranslateService);
  private authService = inject(AuthService);
  private metricsService = inject(MetricsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private responsive = inject(ResponsiveService);
  stravaSyncService = inject(StravaSyncService);
  private readonly raceGoalService = inject(RaceGoalService);

  sessions$ = this.historyService.historySessions$;
  historyState$ = this.historyService.historyState$;

  /** Set of YYYY-MM-DD dates on which the athlete has a race goal — drives the race-day chip. */
  private raceDates$ = this.raceGoalService.goals$.pipe(
    map((goals) => {
      const set = new Set<string>();
      for (const g of goals) {
        const d = goalDate(g);
        if (d) set.add(d);
      }
      return set;
    }),
  );
  raceDatesValue = new Set<string>();
  sidebarCollapsed = false;

  /** Sessions sharing a groupId with the selected one (chronological). Empty
   * when the selected session isn't linked. Drives the switcher pill row in
   * the analysis top bar. */
  linkedSessions$ = combineLatest([this.historyService.selectedSession$, this.sessions$]).pipe(
    map(([sel, all]) => {
      if (!sel?.groupId) return [];
      const members = all.filter((s) => s.groupId === sel.groupId);
      if (members.length < 2) return [];
      return [...members].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    }),
  );

  /**
   * Detail mode is driven entirely by the URL:
   *   /history             → list, no detail
   *   /history/:sessionId  → detail of that session
   *
   * The native back button works for free; mobile flow is pure CSS.
   */
  sessionIdParam$ = this.route.paramMap.pipe(map((p) => p.get('sessionId')));
  isListView$ = this.sessionIdParam$.pipe(map((id) => !id));

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  ngOnInit(): void {
    // Cache the race-date set synchronously so per-row chip predicates stay cheap.
    this.raceDates$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((set) => {
      this.raceDatesValue = set;
      this.toggleSubject.next();
    });

    // Sync the service-level selectedSession with the route param so any
    // downstream consumer that reads `historyService.selectedSession$`
    // sees the current focus.
    combineLatest([this.sessionIdParam$, this.sessions$])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([id, sessions]) => {
        if (!id) {
          this.historyService.selectSession(null);
          return;
        }
        if (sessions.length === 0) return;
        const match = sessions.find((s) => s.id === id);
        this.historyService.selectSession(match ?? null);
      });

    // On desktop, when the user lands on bare /history, jump them to the
    // first session so the detail panel isn't empty.
    combineLatest([this.sessions$, this.responsive.isMobile$, this.sessionIdParam$])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(([sessions, mobile, id]) => {
        if (!mobile && !id && sessions.length > 0) {
          this.router.navigate(['/history', sessions[0].id], { replaceUrl: true });
        }
      });

    // Push filter changes server-side; debounce so each digit typed in
    // numeric inputs doesn't issue its own request.
    combineLatest([
      this.sportFilterSubject,
      this.dateFromSubject,
      this.dateToSubject,
      this.durationMinSubject,
      this.durationMaxSubject,
      this.tssMinSubject,
      this.tssMaxSubject,
    ])
      .pipe(
        // Skip the initial emission — the service has already loaded the
        // unfiltered first window in its constructor.
        debounceTime(300),
        map(
          ([sport, from, to, durMin, durMax, tssMin, tssMax]): SessionFilters => ({
            sport,
            from: from || null,
            to: to || null,
            durationMinSec: durMin != null ? durMin * 60 : null,
            durationMaxSec: durMax != null ? durMax * 60 : null,
            tssMin,
            tssMax,
          }),
        ),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((filters) => {
        if (this.hasAnyFilter(filters)) {
          this.historyService.setHistoryFilters(filters);
        } else if (this.lastAppliedHadFilters) {
          // Filters were cleared — reload unfiltered first window.
          this.historyService.setHistoryFilters({});
        }
        this.lastAppliedHadFilters = this.hasAnyFilter(filters);
      });
  }

  private lastAppliedHadFilters = false;

  private hasAnyFilter(f: SessionFilters): boolean {
    return !!(
      f.sport ||
      f.from ||
      f.to ||
      f.durationMinSec != null ||
      f.durationMaxSec != null ||
      f.tssMin != null ||
      f.tssMax != null
    );
  }

  loadOlder(): void {
    this.historyService.loadOlderHistory();
  }

  // Filters — labels translated via instant (language known at component init)
  get sportOptions() {
    return [
      { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_ALL'), value: null },
      { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_BIKE'), value: 'CYCLING' },
      { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_RUN'), value: 'RUNNING' },
      { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_SWIM'), value: 'SWIMMING' },
    ];
  }

  private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
  private dateFromSubject = new BehaviorSubject<string>('');
  private dateToSubject = new BehaviorSubject<string>('');
  private durationMinSubject = new BehaviorSubject<number | null>(null);
  private durationMaxSubject = new BehaviorSubject<number | null>(null);
  private tssMinSubject = new BehaviorSubject<number | null>(null);
  private tssMaxSubject = new BehaviorSubject<number | null>(null);

  activeSportFilter: SportFilter = null;
  dateFrom = '';
  dateTo = '';
  durationMin: number | null = null;
  durationMax: number | null = null;
  tssMin: number | null = null;
  tssMax: number | null = null;

  filtersOpen = false;

  get activeAdvancedFilterCount(): number {
    let n = 0;
    if (this.dateFrom) n++;
    if (this.dateTo) n++;
    if (this.durationMin != null) n++;
    if (this.durationMax != null) n++;
    if (this.tssMin != null) n++;
    if (this.tssMax != null) n++;
    return n;
  }

  openFilters(): void {
    this.filtersOpen = true;
  }

  closeFilters(): void {
    this.filtersOpen = false;
  }

  resetAdvancedFilters(): void {
    this.onDateFromChange('');
    this.onDateToChange('');
    this.onDurationMinChange(null);
    this.onDurationMaxChange(null);
    this.onTssMinChange(null);
    this.onTssMaxChange(null);
  }

  ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? null));

  // Week grouping state — tracks which week keys the user has expanded.
  // Seeded once on the first non-empty emission so the most recent active
  // week starts open; subsequent toggles are fully manual.
  expandedWeeks = new Set<string>();
  /** Race/brick groups the user has drilled into. Collapsed by default — the
   *  group card is the primary visual and shows aggregate metrics. */
  expandedGroups = new Set<string>();
  private expandedSeeded = false;
  private toggleSubject = new BehaviorSubject<void>(undefined);

  groupedRows$ = combineLatest([this.sessions$, this.toggleSubject]).pipe(
    map(([sessions]) => {
      if (!this.expandedSeeded && sessions.length > 0) {
        this.expandedWeeks.add(weekKeyOf(new Date(sessions[0].date)));
        this.expandedSeeded = true;
      }
      return buildHistoryRows(sessions, this.expandedWeeks, this.expandedGroups);
    }),
  );

  toggleWeek(weekKey: string): void {
    if (this.expandedWeeks.has(weekKey)) {
      this.expandedWeeks.delete(weekKey);
    } else {
      this.expandedWeeks.add(weekKey);
    }
    this.toggleSubject.next();
  }

  isExpanded(weekKey: string): boolean {
    return this.expandedWeeks.has(weekKey);
  }

  toggleGroup(groupKey: string, event: Event): void {
    event.stopPropagation();
    if (this.expandedGroups.has(groupKey)) {
      this.expandedGroups.delete(groupKey);
    } else {
      this.expandedGroups.add(groupKey);
    }
    this.toggleSubject.next();
  }

  isGroupExpanded(groupKey: string): boolean {
    return this.expandedGroups.has(groupKey);
  }

  /** Translated sport tokens used to render brick titles ("Bike → Run"). */
  private get sportLabels(): Record<string, string> {
    return {
      CYCLING: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_BIKE'),
      RUNNING: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_RUN'),
      SWIMMING: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_SWIM'),
      BRICK: 'Brick',
    };
  }

  groupTitle(group: SessionGroup): string {
    const raw = groupTitleFor(group, this.sportLabels);
    if (group.kind === 'race') {
      const i18nKey =
        raw === 'Triathlon'
          ? 'WORKOUT_HISTORY.GROUP_TRIATHLON'
          : 'WORKOUT_HISTORY.GROUP_MULTISPORT';
      return this.translate.instant(i18nKey);
    }
    return raw;
  }

  groupSummaryTss(group: SessionGroup, ftp: number | null): number {
    return group.sessions.reduce((acc, s) => acc + (this.getTss(s, ftp) ?? 0), 0);
  }

  formatWeekRange = formatWeekRange;

  trackRow(_index: number, row: HistoryRow): string {
    if (row.kind === 'session') return `s-${row.session.id}`;
    if (row.kind === 'week') return `w-${row.weekKey}`;
    if (row.kind === 'group') return `g-${row.groupKey}`;
    return row.rowKey;
  }

  setSportFilter(value: SportFilter): void {
    this.activeSportFilter = value;
    this.sportFilterSubject.next(value);
  }

  onDateFromChange(value: string): void {
    this.dateFrom = value;
    this.dateFromSubject.next(value);
  }

  onDateToChange(value: string): void {
    this.dateTo = value;
    this.dateToSubject.next(value);
  }

  onDurationMinChange(value: number | null): void {
    this.durationMin = value;
    this.durationMinSubject.next(value != null && !isNaN(value) ? value : null);
  }

  onDurationMaxChange(value: number | null): void {
    this.durationMax = value;
    this.durationMaxSubject.next(value != null && !isNaN(value) ? value : null);
  }

  onTssMinChange(value: number | null): void {
    this.tssMin = value;
    this.tssMinSubject.next(value != null && !isNaN(value) ? value : null);
  }

  onTssMaxChange(value: number | null): void {
    this.tssMax = value;
    this.tssMaxSubject.next(value != null && !isNaN(value) ? value : null);
  }

  syncing$ = this.stravaSyncService.syncing$;
  syncResult$ = this.stravaSyncService.lastResult$;

  importing = false;
  importError = false;

  manualModalOpen = false;
  @ViewChild(ManualSessionModalComponent) manualModal?: ManualSessionModalComponent;

  openManualModal(): void {
    this.manualModalOpen = true;
  }

  closeManualModal(): void {
    this.manualModalOpen = false;
  }

  // ── Manual link state ──────────────────────────────────────────
  linkModalOpen = false;
  linkAnchor: SavedSession | null = null;
  linkCandidates: SavedSession[] = [];
  @ViewChild(LinkSessionsModalComponent) linkModal?: LinkSessionsModalComponent;

  /** Sessions on the same calendar day as the anchor, excluding the anchor itself. */
  private sameDayCandidates(anchor: SavedSession, all: SavedSession[]): SavedSession[] {
    const day = new Date(anchor.date);
    const dayKey = `${day.getFullYear()}-${day.getMonth()}-${day.getDate()}`;
    return all.filter((s) => {
      if (s.id === anchor.id) return false;
      const d = new Date(s.date);
      return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}` === dayKey;
    });
  }

  openLinkModal(session: SavedSession, event: Event): void {
    event.stopPropagation();
    // Snapshot the current list — sessions$ is an observable, but we only need a
    // one-shot read for the candidate pool.
    this.sessions$.pipe(take(1)).subscribe((all) => {
      this.linkAnchor = session;
      this.linkCandidates = this.sameDayCandidates(session, all);
      this.linkModalOpen = true;
    });
  }

  closeLinkModal(): void {
    this.linkModalOpen = false;
    this.linkAnchor = null;
    this.linkCandidates = [];
  }

  // ── Link to scheduled workout (adherence) ──────────────────────
  linkScheduleModalOpen = false;
  linkScheduleSession: SavedSession | null = null;

  openLinkScheduleModal(session: SavedSession, event: Event): void {
    event.stopPropagation();
    this.linkScheduleSession = session;
    this.linkScheduleModalOpen = true;
  }

  closeLinkScheduleModal(): void {
    this.linkScheduleModalOpen = false;
    this.linkScheduleSession = null;
  }

  onScheduleLinked(_scheduledWorkoutId: string): void {
    // history.service already mirrored the change locally; just close the modal.
    this.closeLinkScheduleModal();
  }

  onMarkedUnplanned(): void {
    this.closeLinkScheduleModal();
  }

  /** A session that needs the user's attention: orphan + has a pending candidate stored. */
  hasPendingSuggestion(session: SavedSession): boolean {
    return (
      !session.scheduledWorkoutId &&
      !session.unplanned &&
      !!session.suggestedScheduledWorkoutId &&
      !this.hasUnclassifiedRaceDay(session)
    );
  }

  /** A session on a date the athlete has a race goal for, and not yet classified. */
  hasUnclassifiedRaceDay(session: SavedSession): boolean {
    if (session.raceRole) return false;
    const iso = toIsoDate(session.date);
    return this.raceDatesValue.has(iso);
  }

  /** Pending action for a session — drives the list dot and the inline detail panel.
   *  Race classification takes priority over a plan-link suggestion. */
  pendingActionFor(session: SavedSession): SessionActionKind | null {
    if (this.hasUnclassifiedRaceDay(session)) return 'race';
    if (this.hasPendingSuggestion(session)) return 'link';
    return null;
  }

  onLinkApply(change: LinkChange): void {
    if (!this.linkAnchor) return;
    const anchorId = this.linkAnchor.id;

    // Build the patch set: members get groupId; removed sessions get null.
    const patches: Array<{ id: string; groupId: string | null }> = [];
    if (change.memberIds.length === 0) {
      // Unlink anchor (and clear any leftover group siblings).
      patches.push({ id: anchorId, groupId: null });
      for (const r of change.removedIds) patches.push({ id: r, groupId: null });
    } else {
      for (const id of change.memberIds) patches.push({ id, groupId: change.groupId });
      for (const r of change.removedIds) patches.push({ id: r, groupId: null });
    }

    if (patches.length === 0) {
      this.linkModal?.finish();
      this.closeLinkModal();
      return;
    }

    const updates$ = patches.map((p) => this.historyService.setSessionGroup(p.id, p.groupId));
    forkJoin(updates$.length ? updates$ : [of(null)]).subscribe({
      next: () => {
        this.linkModal?.finish();
        this.closeLinkModal();
      },
      error: () => this.linkModal?.finish(),
    });
  }

  onManualCreate(payload: ManualSessionInput): void {
    this.historyService.createManualSession(payload).subscribe({
      next: () => {
        this.manualModal?.clearAndClose();
        this.manualModalOpen = false;
      },
      error: () => this.manualModal?.setError('Could not save session — please try again.'),
    });
  }

  getTss(session: SavedSession, ftp: number | null): number | null {
    if (session.tss != null) return Math.round(session.tss);
    if (!ftp) return null;
    return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
  }

  /** Effective plan-alignment percentage for the list badge (coach rating, else athlete's). */
  alignmentScore(session: SavedSession): number | null {
    return effectiveAlignmentScore(session.alignmentScore);
  }

  /** On-target (green) vs off-target (red) zone for the corner badge; null when unrated. */
  alignmentZoneFor(session: SavedSession): AlignmentZone | null {
    const score = this.alignmentScore(session);
    return score == null ? null : alignmentZone(score);
  }

  getIF(session: SavedSession, ftp: number | null): number | null {
    if (session.intensityFactor != null) return session.intensityFactor;
    if (!ftp) return null;
    return this.metricsService.computeIF(session.avgPower, ftp);
  }

  importStravaHistory(): void {
    this.stravaSyncService.importHistory().subscribe();
  }

  exportCsv(): void {
    combineLatest([this.sessions$, this.ftp$])
      .pipe(take(1))
      .subscribe(([sessions, ftp]) => {
        this.csvExport.exportSessions(sessions, ftp);
      });
  }

  onSelect(session: SavedSession): void {
    this.router.navigate(['/history', session.id]);
  }

  onSelectById(sessionId: string): void {
    this.router.navigate(['/history', sessionId]);
  }

  onSelectGroup(group: SessionGroup): void {
    if (group.sessions.length === 0) return;
    this.onSelect(group.sessions[0]);
  }

  isCurrentGroup(selectedId: string | null | undefined, group: SessionGroup): boolean {
    if (!selectedId) return false;
    return group.sessions.some((s) => s.id === selectedId);
  }

  downloadFit(event: Event, session: SavedSession) {
    event.stopPropagation();
    if (session.stravaActivityId && !session.fitFileId) {
      // Strava session without FIT — build it from streams first, then download
      this.stravaSyncService.buildFit(session.id).subscribe({
        next: () => this.fitExport.exportSession(session, session.date),
      });
    } else {
      this.fitExport.exportSession(session, session.date);
    }
  }

  /** Header toolbar triggers — currently the analysis top bar is the only
   *  surface that exposes link/download for the focused session. */
  onAnalysisLinkClicked(session: SavedSession): void {
    this.sessions$.pipe(take(1)).subscribe((all) => {
      this.linkAnchor = session;
      this.linkCandidates = this.sameDayCandidates(session, all);
      this.linkModalOpen = true;
    });
  }

  onAnalysisDownloadClicked(session: SavedSession): void {
    if (session.stravaActivityId && !session.fitFileId) {
      this.stravaSyncService.buildFit(session.id).subscribe({
        next: () => this.fitExport.exportSession(session, session.date),
      });
    } else {
      this.fitExport.exportSession(session, session.date);
    }
  }

  triggerUpload() {
    this.fitInputRef.nativeElement.click();
  }

  async onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';

    this.importing = true;
    this.importError = false;

    try {
      const buffer = await file.arrayBuffer();
      const session = await parseFitToSession(file.name, buffer);
      this.historyService.saveSession(session, buffer);
    } catch (e) {
      console.error('Failed to import FIT file', e);
      this.importError = true;
      setTimeout(() => (this.importError = false), 4000);
    } finally {
      this.importing = false;
    }
  }

  formatTime(seconds: number): string {
    return formatTimeText(seconds);
  }

  /** Compact "40m" / "1h 10m" matching the session card design. */
  formatShortDuration(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return m ? `${h}h ${m}m` : `${h}h`;
    return `${m}m`;
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /** Short "May 12" date for the inline session-card meta row. */
  formatShortDate(date: Date): string {
    return new Date(date).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
    });
  }

  private static readonly SPORT_COLORS: Record<string, string> = {
    CYCLING: 'var(--success-color)',
    RUNNING: 'var(--danger-color)',
    SWIMMING: 'var(--secondary-color)',
    BRICK: 'var(--accent-color)',
  };

  sportColor(sport: string | null | undefined): string {
    return WorkoutHistoryComponent.SPORT_COLORS[sport ?? ''] ?? 'var(--text-muted)';
  }

  getSportUnit(session: SavedSession): string {
    if (session.sportType === 'RUNNING') return '/km';
    if (session.sportType === 'SWIMMING') return '/100m';
    return 'W';
  }
}
