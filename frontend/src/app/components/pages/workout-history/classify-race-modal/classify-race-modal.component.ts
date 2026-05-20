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
import { BehaviorSubject, of } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ModalShellComponent } from '../../../shared/modal-shell/modal-shell.component';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import {
  HistoryService,
  RaceCandidate,
  RaceRole,
  SavedSession,
} from '../../../../services/history.service';

/**
 * Race-day classification picker: lets the athlete tag a session as RACE (counts toward the
 * race chain) or WARMUP (race-day but separate), or dismiss the prompt (role=NONE).
 */
@Component({
  selector: 'app-classify-race-modal',
  standalone: true,
  imports: [CommonModule, TranslateModule, ModalShellComponent, SportIconComponent],
  templateUrl: './classify-race-modal.component.html',
  styleUrl: './classify-race-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassifyRaceModalComponent implements OnChanges {
  @Input() open = false;
  @Input() session: SavedSession | null = null;
  @Output() closed = new EventEmitter<void>();
  /** Emits the chosen role once the classification has been persisted. */
  @Output() classified = new EventEmitter<RaceRole>();

  private readonly historyService = inject(HistoryService);

  private candidatesSubject = new BehaviorSubject<RaceCandidate[]>([]);
  candidates$ = this.candidatesSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  private submittingSubject = new BehaviorSubject<boolean>(false);
  submitting$ = this.submittingSubject.asObservable();

  selectedRaceId: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open && this.session) {
      this.loadCandidates(this.session.id);
    }
    if (!this.open) {
      this.selectedRaceId = null;
    }
  }

  private loadCandidates(sessionId: string): void {
    this.loadingSubject.next(true);
    this.candidatesSubject.next([]);
    this.historyService
      .getRaceCandidates(sessionId)
      .pipe(
        tap((list) => {
          this.candidatesSubject.next(list);
          // Pre-select the previously chosen race if any, else first candidate.
          const existing = this.session?.raceId;
          this.selectedRaceId =
            (existing && list.find((c) => c.id === existing)?.id) ||
            list[0]?.id ||
            null;
        }),
        catchError(() => {
          this.candidatesSubject.next([]);
          return of([] as RaceCandidate[]);
        }),
        finalize(() => this.loadingSubject.next(false)),
      )
      .subscribe();
  }

  selectRace(id: string): void {
    this.selectedRaceId = id;
  }

  submitRole(role: RaceRole): void {
    if (!this.session) return;
    // RACE / WARMUP require a selected race; NONE doesn't.
    if (role !== 'NONE' && !this.selectedRaceId) return;

    this.submittingSubject.next(true);
    const raceId = role === 'NONE' ? null : this.selectedRaceId;
    this.historyService
      .classifyRace(this.session.id, raceId, role)
      .pipe(finalize(() => this.submittingSubject.next(false)))
      .subscribe({
        next: () => this.classified.emit(role),
      });
  }

  close(): void {
    this.closed.emit();
  }

  trackByCandidate = (_i: number, c: RaceCandidate): string => c.id;
}
