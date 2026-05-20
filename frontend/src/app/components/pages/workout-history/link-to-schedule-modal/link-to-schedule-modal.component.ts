import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ModalShellComponent } from '../../../shared/modal-shell/modal-shell.component';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { HistoryService, LinkCandidate, SavedSession } from '../../../../services/history.service';

/**
 * Picker that lets the athlete confirm or correct the link between a completed session and a
 * pending scheduled workout. Candidates come from the backend ranked by score, with a
 * sport/date/duration/title breakdown so the user can judge confidence.
 */
@Component({
  selector: 'app-link-to-schedule-modal',
  standalone: true,
  imports: [CommonModule, TranslateModule, ModalShellComponent, SportIconComponent],
  templateUrl: './link-to-schedule-modal.component.html',
  styleUrl: './link-to-schedule-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkToScheduleModalComponent implements OnChanges {
  @Input() open = false;
  @Input() session: SavedSession | null = null;
  @Output() closed = new EventEmitter<void>();
  /** Emits the chosen scheduledWorkoutId after the link succeeds. */
  @Output() linked = new EventEmitter<string>();
  /** User confirmed this session was not part of any planned workout. */
  @Output() unplanned = new EventEmitter<void>();

  private readonly historyService = inject(HistoryService);

  private candidatesSubject = new BehaviorSubject<LinkCandidate[]>([]);
  candidates$ = this.candidatesSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  private submittingSubject = new BehaviorSubject<boolean>(false);
  submitting$ = this.submittingSubject.asObservable();

  selectedId: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open && this.session) {
      this.loadCandidates(this.session.id);
    }
    if (!this.open) {
      this.selectedId = null;
    }
  }

  private loadCandidates(sessionId: string): void {
    this.loadingSubject.next(true);
    this.candidatesSubject.next([]);
    this.historyService
      .getLinkCandidates(sessionId)
      .pipe(
        tap((list) => {
          this.candidatesSubject.next(list);
          const suggested = this.session?.suggestedScheduledWorkoutId;
          this.selectedId =
            (suggested && list.find((c) => c.scheduledWorkoutId === suggested)?.scheduledWorkoutId) ||
            list[0]?.scheduledWorkoutId ||
            null;
        }),
        catchError(() => {
          this.candidatesSubject.next([]);
          return of([] as LinkCandidate[]);
        }),
        finalize(() => this.loadingSubject.next(false)),
      )
      .subscribe();
  }

  select(id: string): void {
    this.selectedId = id;
  }

  confirm(): void {
    if (!this.session || !this.selectedId) return;
    this.submittingSubject.next(true);
    this.historyService
      .linkToScheduledWorkout(this.session.id, this.selectedId)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe({
        next: () => this.linked.emit(this.selectedId!),
        error: () => {
          // Toast surfaces via global error interceptor — just keep modal open.
        },
      });
  }

  declareUnplanned(): void {
    if (!this.session) return;
    this.submittingSubject.next(true);
    this.historyService
      .markUnplanned(this.session.id)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe({
        next: () => this.unplanned.emit(),
      });
  }

  close(): void {
    this.closed.emit();
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

  trackByCandidate = (_i: number, c: LinkCandidate): string => c.scheduledWorkoutId;
}
