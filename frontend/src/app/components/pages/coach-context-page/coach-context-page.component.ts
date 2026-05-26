import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { ContextService } from '../../../services/context.service';
import {
  COACH_PHILOSOPHY_SECTIONS,
  COACH_PHILOSOPHY_SECTION_GUIDANCE,
  ContextSections,
} from '../../../models/context.model';
import { ContextEditorComponent } from '../../shared/context-editor/context-editor.component';

/**
 * Standalone page for the coach's own coaching philosophy. Reached from the Coaching nav dropdown
 * (coach-only). The athlete self-context — which a coach also has, since they train themselves —
 * lives on the separate Training → Context page. Reuses {@link ContextEditorComponent} and the
 * shared {@link ContextService}.
 */
@Component({
  selector: 'app-coach-context-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ContextEditorComponent],
  templateUrl: './coach-context-page.component.html',
  styleUrl: './coach-context-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachContextPageComponent implements OnInit {
  private contextService = inject(ContextService);

  readonly coachSections = COACH_PHILOSOPHY_SECTIONS;
  readonly coachGuidance = COACH_PHILOSOPHY_SECTION_GUIDANCE;

  myCoachContext$ = this.contextService.myCoachContext$;
  loading$ = this.contextService.coachLoading$;

  private savingSubject = new BehaviorSubject<boolean>(false);
  saving$ = this.savingSubject.asObservable();

  ngOnInit(): void {
    this.contextService.loadMyCoachContext();
  }

  onSaveContext(sections: ContextSections): void {
    this.savingSubject.next(true);
    this.contextService.saveMyCoachContext(sections).subscribe({
      next: () => this.savingSubject.next(false),
      error: () => this.savingSubject.next(false),
    });
  }
}
