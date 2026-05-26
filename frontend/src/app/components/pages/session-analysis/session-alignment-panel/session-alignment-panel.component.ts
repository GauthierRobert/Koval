import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { SavedSession } from '../../../../services/history.service';
import { AlignmentScore, effectiveAlignmentScore } from '../../../../models/alignment.model';
import { AlignmentBadgeComponent } from '../../../shared/alignment-badge/alignment-badge.component';
import {
  AlignmentMode,
  AlignmentScoreModalComponent,
} from '../../../shared/alignment-score-modal/alignment-score-modal.component';

/**
 * Plan-alignment strip shown below the session's main metrics when it is linked to a scheduled
 * workout. Surfaces the athlete and coach/AI ratings and opens the rating modal — in athlete mode
 * for the session owner, in coach mode when a coach is viewing a managed athlete's session.
 */
@Component({
  selector: 'app-session-alignment-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule, AlignmentBadgeComponent, AlignmentScoreModalComponent],
  templateUrl: './session-alignment-panel.component.html',
  styleUrl: './session-alignment-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionAlignmentPanelComponent {
  /** When set, the viewer is a coach looking at this athlete's session (coach-rating mode). */
  @Input() coachAthleteId: string | null = null;

  @Output() changed = new EventEmitter<AlignmentScore>();

  private _session!: SavedSession;
  alignment: AlignmentScore | null = null;

  @Input({ required: true }) set session(s: SavedSession) {
    this._session = s;
    this.alignment = s?.alignmentScore ?? null;
  }
  get session(): SavedSession {
    return this._session;
  }

  modalOpen = false;
  modalMode: AlignmentMode = 'athlete';

  get isCoachView(): boolean {
    return this.coachAthleteId != null;
  }

  get linked(): boolean {
    return !!this.session?.scheduledWorkoutId;
  }

  get effectiveScore(): number | null {
    return effectiveAlignmentScore(this.alignment);
  }

  get athleteScore(): number | null {
    return this.alignment?.athleteScore ?? null;
  }

  get coachScore(): number | null {
    return this.alignment?.coachScore ?? null;
  }

  get coachIsAi(): boolean {
    return this.alignment?.coachSource === 'ai';
  }

  openModal(): void {
    this.modalMode = this.isCoachView ? 'coach' : 'athlete';
    this.modalOpen = true;
  }

  onSaved(updated: AlignmentScore): void {
    this.alignment = updated;
    this.modalOpen = false;
    this.changed.emit(updated);
  }

  closeModal(): void {
    this.modalOpen = false;
  }
}
