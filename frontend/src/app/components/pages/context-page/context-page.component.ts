import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { ContextService } from '../../../services/context.service';
import {
  ATHLETE_CONTEXT_SECTIONS,
  ATHLETE_CONTEXT_SECTION_GUIDANCE,
  COACH_PHILOSOPHY_SECTIONS,
  COACH_PHILOSOPHY_SECTION_GUIDANCE,
  ContextSections,
} from '../../../models/context.model';
import { ContextEditorComponent } from '../../shared/context-editor/context-editor.component';

/**
 * Standalone page for the caller's own training context (athlete self-context or coach philosophy).
 * Reached from the Training nav dropdown. Reuses {@link ContextEditorComponent} and the shared
 * {@link ContextService}; previously this editor lived inside the Settings modal.
 */
@Component({
  selector: 'app-context-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ContextEditorComponent],
  templateUrl: './context-page.component.html',
  styleUrl: './context-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextPageComponent implements OnInit {
  private contextService = inject(ContextService);

  readonly athleteSections = ATHLETE_CONTEXT_SECTIONS;
  readonly coachSections = COACH_PHILOSOPHY_SECTIONS;
  readonly athleteGuidance = ATHLETE_CONTEXT_SECTION_GUIDANCE;
  readonly coachGuidance = COACH_PHILOSOPHY_SECTION_GUIDANCE;

  myContext$ = this.contextService.myContext$;
  loading$ = this.contextService.loading$;

  private savingSubject = new BehaviorSubject<boolean>(false);
  saving$ = this.savingSubject.asObservable();

  ngOnInit(): void {
    this.contextService.loadMyContext();
  }

  onSaveContext(sections: ContextSections): void {
    this.savingSubject.next(true);
    this.contextService.saveMyContext(sections).subscribe({
      next: () => this.savingSubject.next(false),
      error: () => this.savingSubject.next(false),
    });
  }
}
