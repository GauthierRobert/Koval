import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, combineLatest, of } from 'rxjs';
import { catchError, finalize, map, tap } from 'rxjs/operators';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import {
  HistoryService,
  LinkCandidate,
  RaceCandidate,
  RaceRole,
  SavedSession,
} from '../../../../services/history.service';

/** Which pending action the panel resolves. Mirrors the colored dot in the history list. */
export type SessionActionKind = 'race' | 'link';

/**
 * Inline panel rendered directly under the session stats header when a session has a pending
 * action: a sub-threshold link suggestion ('link') or an unclassified race-day ('race'). It
 * replaces the old list chips + modals — the athlete confirms the link or classifies the race
 * without leaving the detail view. All mutations go through HistoryService, which mirrors the
 * change onto local state, so both this panel and the list dot clear reactively.
 */
@Component({
  selector: 'app-session-action-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './session-action-panel.component.html',
  styleUrl: './session-action-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionActionPanelComponent implements OnChanges {
  @Input() session: SavedSession | null = null;
  @Input() kind: SessionActionKind | null = null;
  /** False when a coach is viewing — the linked race is shown read-only (only the owner can re-link/unlink). */
  @Input() canEdit = true;

  private readonly historyService = inject(HistoryService);

  private linkCandidatesSubject = new BehaviorSubject<LinkCandidate[]>([]);
  linkCandidates$ = this.linkCandidatesSubject.asObservable();

  private raceCandidatesSubject = new BehaviorSubject<RaceCandidate[]>([]);
  raceCandidates$ = this.raceCandidatesSubject.asObservable();

  private sessionSubject = new BehaviorSubject<SavedSession | null>(null);

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  private submittingSubject = new BehaviorSubject<boolean>(false);
  submitting$ = this.submittingSubject.asObservable();

  /** When true the panel shows the classify picker instead of the linked summary (the "Change" flow). */
  private editingSubject = new BehaviorSubject<boolean>(false);
  editing$ = this.editingSubject.asObservable();

  /** The race the session is currently linked to, enriched from the candidate list when available. */
  linkedRace$ = combineLatest([this.sessionSubject, this.raceCandidatesSubject]).pipe(
    map(([session, candidates]) =>
      session?.raceId ? (candidates.find((c) => c.id === session.raceId) ?? null) : null,
    ),
  );

  selectedLinkId: string | null = null;
  selectedRaceId: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['session'] && !changes['kind']) return;
    const session = this.session;
    this.sessionSubject.next(session);
    this.editingSubject.next(false);
    if (!session || !this.kind) {
      this.linkCandidatesSubject.next([]);
      this.raceCandidatesSubject.next([]);
      return;
    }
    if (this.kind === 'link') {
      this.loadLinkCandidates(session);
    } else {
      this.loadRaceCandidates(session);
    }
  }

  /** True once the session carries a committed race classification (RACE or WARMUP). */
  isLinkedRace(session: SavedSession | null): boolean {
    return !!session?.raceId && (session.raceRole === 'RACE' || session.raceRole === 'WARMUP');
  }

  /** i18n key for the linked-state role badge. */
  roleLabelKey(role: RaceRole | undefined): string {
    return role === 'WARMUP'
      ? 'WORKOUT_HISTORY.RACE_LINKED_ROLE_WARMUP'
      : 'WORKOUT_HISTORY.RACE_LINKED_ROLE_RACE';
  }

  /** Switch from the linked summary to the classify picker so the user can pick a different race/role. */
  startEditing(): void {
    this.editingSubject.next(true);
  }

  /** Abandon the "Change" flow and return to the linked summary. */
  cancelEditing(): void {
    this.editingSubject.next(false);
  }

  /** Remove the race classification entirely; the panel reverts to the unclassified prompt. */
  unlink(): void {
    if (!this.session) return;
    this.submittingSubject.next(true);
    this.historyService
      .unclassifyRace(this.session.id)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe(() => this.editingSubject.next(false));
  }

  private loadLinkCandidates(session: SavedSession): void {
    this.loadingSubject.next(true);
    this.linkCandidatesSubject.next([]);
    this.historyService
      .getLinkCandidates(session.id)
      .pipe(
        tap((list) => {
          this.linkCandidatesSubject.next(list);
          const suggested = session.suggestedScheduledWorkoutId;
          this.selectedLinkId =
            (suggested &&
              list.find((c) => c.scheduledWorkoutId === suggested)?.scheduledWorkoutId) ||
            list[0]?.scheduledWorkoutId ||
            null;
        }),
        catchError(() => {
          this.linkCandidatesSubject.next([]);
          return of([] as LinkCandidate[]);
        }),
        finalize(() => this.loadingSubject.next(false)),
      )
      .subscribe();
  }

  private loadRaceCandidates(session: SavedSession): void {
    this.loadingSubject.next(true);
    this.raceCandidatesSubject.next([]);
    this.historyService
      .getRaceCandidates(session.id)
      .pipe(
        tap((list) => {
          this.raceCandidatesSubject.next(list);
          const existing = session.raceId;
          this.selectedRaceId =
            (existing && list.find((c) => c.id === existing)?.id) || list[0]?.id || null;
        }),
        catchError(() => {
          this.raceCandidatesSubject.next([]);
          return of([] as RaceCandidate[]);
        }),
        finalize(() => this.loadingSubject.next(false)),
      )
      .subscribe();
  }

  selectLink(id: string): void {
    this.selectedLinkId = id;
  }

  selectRace(id: string): void {
    this.selectedRaceId = id;
  }

  confirmLink(): void {
    if (!this.session || !this.selectedLinkId) return;
    this.submittingSubject.next(true);
    this.historyService
      .linkToScheduledWorkout(this.session.id, this.selectedLinkId)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe();
  }

  declareUnplanned(): void {
    if (!this.session) return;
    this.submittingSubject.next(true);
    this.historyService
      .markUnplanned(this.session.id)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe();
  }

  classify(role: RaceRole): void {
    if (!this.session) return;
    // RACE / WARMUP require a selected race; NONE doesn't.
    if (role !== 'NONE' && !this.selectedRaceId) return;
    this.submittingSubject.next(true);
    const raceId = role === 'NONE' ? null : this.selectedRaceId;
    this.historyService
      .classifyRace(this.session.id, raceId, role)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe(() => this.editingSubject.next(false));
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  formatDuration(seconds: number | null): string {
    if (!seconds) return '—';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return m ? `${h}h ${m}m` : `${h}h`;
    return `${m}m`;
  }

  /** Visual cue per sub-score: ✓ when full, ~ when partial, · when zero. */
  scoreCue(value: number, max: number): 'full' | 'partial' | 'none' {
    if (value <= 0) return 'none';
    if (value >= max) return 'full';
    return 'partial';
  }

  trackByLink = (_i: number, c: LinkCandidate): string => c.scheduledWorkoutId;
  trackByRace = (_i: number, c: RaceCandidate): string => c.id;
}
