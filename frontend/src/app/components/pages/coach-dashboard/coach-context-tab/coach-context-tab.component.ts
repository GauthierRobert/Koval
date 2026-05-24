import { ChangeDetectionStrategy, Component, inject, Input, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { ContextService } from '../../../../services/context.service';
import {
  ATHLETE_CONTEXT_SECTIONS,
  COACH_ATHLETE_CONTEXT_SECTIONS,
  CoachAthleteContext,
  ContextSections,
} from '../../../../models/context.model';
import { ContextEditorComponent } from '../../../shared/context-editor/context-editor.component';

/**
 * Coach view of an athlete's context: the athlete's self-context (read-only) plus the coach's
 * own private context about the athlete (editable). The coach's notes are never shown to the
 * athlete — the backend only ever returns the calling coach's own entry here.
 */
@Component({
  selector: 'app-coach-context-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, ContextEditorComponent],
  templateUrl: './coach-context-tab.component.html',
  styleUrl: './coach-context-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachContextTabComponent {
  readonly athleteSections = ATHLETE_CONTEXT_SECTIONS;
  readonly coachAthleteSections = COACH_ATHLETE_CONTEXT_SECTIONS;

  private athleteIdValue = '';
  @Input() set athleteId(value: string) {
    this.athleteIdValue = value;
    this.load();
  }
  get athleteId(): string {
    return this.athleteIdValue;
  }

  private contextSubject = new BehaviorSubject<CoachAthleteContext | null>(null);
  context$ = this.contextSubject.asObservable();

  private savingSubject = new BehaviorSubject<boolean>(false);
  saving$ = this.savingSubject.asObservable();

  private readonly contextService = inject(ContextService);
  private readonly ngZone = inject(NgZone);

  private load(): void {
    if (!this.athleteIdValue) return;
    this.contextSubject.next(null);
    this.contextService.getAthleteContext(this.athleteIdValue).subscribe({
      next: (ctx) => this.ngZone.run(() => this.contextSubject.next(ctx)),
      error: () =>
        this.ngZone.run(() => this.contextSubject.next({ athleteSelf: null, coachContext: null })),
    });
  }

  onSaveCoachContext(sections: ContextSections): void {
    if (!this.athleteIdValue) return;
    this.savingSubject.next(true);
    this.contextService.saveAthleteContext(this.athleteIdValue, sections).subscribe({
      next: (entry) =>
        this.ngZone.run(() => {
          this.savingSubject.next(false);
          const current = this.contextSubject.value;
          this.contextSubject.next({
            athleteSelf: current?.athleteSelf ?? null,
            coachContext: entry,
          });
        }),
      error: () => this.ngZone.run(() => this.savingSubject.next(false)),
    });
  }
}
