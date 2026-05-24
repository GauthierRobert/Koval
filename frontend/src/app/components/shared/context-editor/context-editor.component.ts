import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ContextSections } from '../../../models/context.model';

/**
 * Presentational editor for section-based context (title → markdown). Renders one textarea per
 * section; emits the non-empty sections on save. Reused for athlete self-context, coach
 * philosophy, and a coach's private per-athlete context.
 */
@Component({
  selector: 'app-context-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './context-editor.component.html',
  styleUrl: './context-editor.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextEditorComponent implements OnChanges {
  /** Current stored values (title → content). */
  @Input() sections: ContextSections | null = null;
  /** Recommended section headings to always show, in order. */
  @Input() sectionTitles: readonly string[] = [];
  @Input() readonly = false;
  @Input() saving = false;
  @Input() updatedAt: string | null = null;

  @Output() save = new EventEmitter<ContextSections>();

  model: Record<string, string> = {};
  displayTitles: string[] = [];

  ngOnChanges(): void {
    const stored = this.sections ?? {};
    const ordered = [...this.sectionTitles];
    for (const key of Object.keys(stored)) {
      if (!ordered.includes(key)) ordered.push(key);
    }
    this.displayTitles = ordered;
    this.model = {};
    for (const title of ordered) {
      this.model[title] = stored[title] ?? '';
    }
  }

  onSave(): void {
    const result: ContextSections = {};
    for (const title of this.displayTitles) {
      const value = (this.model[title] ?? '').trim();
      if (value) result[title] = value;
    }
    this.save.emit(result);
  }

  get hasContent(): boolean {
    return this.displayTitles.some((t) => (this.model[t] ?? '').trim().length > 0);
  }
}
