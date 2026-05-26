import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, of } from 'rxjs';
import { AlignmentService } from '../../../services/alignment.service';
import {
  AlignmentEstimate,
  AlignmentScore,
  alignmentZone,
} from '../../../models/alignment.model';
import { ModalShellComponent } from '../modal-shell/modal-shell.component';
import { AlignmentBadgeComponent } from '../alignment-badge/alignment-badge.component';

export type AlignmentMode = 'athlete' | 'coach';

/**
 * Dialog for rating how a completed session matched its scheduled workout. On open it fetches the
 * deterministic estimate to pre-fill a suggestion; in coach mode it also surfaces the athlete's own
 * rating so the coach can adopt, override, or replace it. Saves via {@link AlignmentService} and
 * emits the updated score.
 */
@Component({
  selector: 'app-alignment-score-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, ModalShellComponent, AlignmentBadgeComponent],
  templateUrl: './alignment-score-modal.component.html',
  styleUrl: './alignment-score-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlignmentScoreModalComponent implements OnInit {
  @Input({ required: true }) sessionId!: string;
  @Input() mode: AlignmentMode = 'athlete';
  @Input() alignment: AlignmentScore | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<AlignmentScore>();

  private alignmentService = inject(AlignmentService);
  private cdr = inject(ChangeDetectorRef);

  readonly minScore = 0;
  readonly maxScore = 200;

  score = 100;
  note = '';
  estimate: AlignmentEstimate | null = null;
  estimateLoading = true;
  saving = false;
  error = false;

  ngOnInit(): void {
    const existing =
      this.mode === 'coach' ? this.alignment?.coachScore : this.alignment?.athleteScore;
    const existingNote =
      this.mode === 'coach' ? this.alignment?.coachNote : this.alignment?.athleteNote;
    if (existing != null) this.score = existing;
    if (existingNote) this.note = existingNote;

    this.alignmentService
      .getEstimate(this.sessionId)
      .pipe(catchError(() => of(null)))
      .subscribe((est) => {
        this.estimate = est;
        this.estimateLoading = false;
        // Pre-fill from the estimate only when no rating exists yet for this role.
        if (existing == null && est) this.score = est.score;
        this.cdr.markForCheck();
      });
  }

  get athleteScore(): number | null {
    return this.alignment?.athleteScore ?? null;
  }

  get zoneClass(): string {
    return `zone-${alignmentZone(this.score)}`;
  }

  get title(): string {
    return this.mode === 'coach' ? 'ALIGNMENT.RATE_TITLE_COACH' : 'ALIGNMENT.RATE_TITLE_ATHLETE';
  }

  useEstimate(): void {
    if (this.estimate) this.score = this.estimate.score;
  }

  useAthleteScore(): void {
    if (this.athleteScore != null) this.score = this.athleteScore;
  }

  clampScore(): void {
    if (this.score == null || isNaN(this.score)) this.score = this.minScore;
    this.score = Math.max(this.minScore, Math.min(this.maxScore, Math.round(this.score)));
  }

  factorLabel(name: string): string {
    switch (name) {
      case 'tss':
        return 'ALIGNMENT.FACTOR_TSS';
      case 'if':
        return 'ALIGNMENT.FACTOR_IF';
      case 'duration':
        return 'ALIGNMENT.FACTOR_DURATION';
      case 'blockPower':
        return 'ALIGNMENT.FACTOR_BLOCK_POWER';
      default:
        return name;
    }
  }

  save(): void {
    this.clampScore();
    this.saving = true;
    this.error = false;
    const note = this.note.trim() || null;
    const call$ =
      this.mode === 'coach'
        ? this.alignmentService.setCoachScore(this.sessionId, this.score, note)
        : this.alignmentService.setAthleteScore(this.sessionId, this.score, note);

    call$.subscribe({
      next: (result) => {
        this.saving = false;
        this.saved.emit(result.alignmentScore ?? {});
      },
      error: () => {
        this.saving = false;
        this.error = true;
        this.cdr.markForCheck();
      },
    });
  }
}
