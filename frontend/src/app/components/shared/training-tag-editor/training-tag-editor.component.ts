import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    DestroyRef,
    ElementRef,
    EventEmitter,
    Input,
    Output,
    ViewChild,
    inject,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {map} from 'rxjs/operators';

/**
 * Inline chip-style editor for user-defined training tags.
 *
 * - Display mode: shows tag chips, plus a "+ Add" affordance.
 * - Edit mode: text input with autocomplete pulled from the user's existing
 *   tag vocabulary so they don't accidentally create "intervals", "Intervals",
 *   and "INTERVALS" as three distinct tags.
 * - Pressing Enter (or comma) commits the current input. Pressing Backspace on
 *   an empty input removes the last chip.
 *
 * The component is presentational: the host receives a `(tagsChange)` event
 * with the new tag list and is responsible for persisting it.
 */
@Component({
    selector: 'app-training-tag-editor',
    standalone: true,
    imports: [CommonModule, FormsModule, TranslateModule],
    templateUrl: './training-tag-editor.component.html',
    styleUrl: './training-tag-editor.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrainingTagEditorComponent {
    @Input() set tags(value: string[] | undefined | null) {
        this._tags = value ? [...value] : [];
    }
    get tags(): string[] {
        return this._tags;
    }
    @Input() readonly = false;
    @Output() tagsChange = new EventEmitter<string[]>();

    @ViewChild('input') input?: ElementRef<HTMLInputElement>;

    private filterService = inject(TrainingFilterService);
    private translate = inject(TranslateService);
    private destroyRef = inject(DestroyRef);
    private cdr = inject(ChangeDetectorRef);

    _tags: string[] = [];
    editing = false;
    draft = '';
    suggestions: string[] = [];
    private vocabulary: string[] = [];

    constructor() {
        this.filterService.availableUserTags$
            .pipe(
                map((entries) => entries.map((e) => e.tag)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((vocab) => {
                this.vocabulary = vocab;
                this.refreshSuggestions();
            });
    }

    startEditing(): void {
        if (this.readonly) return;
        this.editing = true;
        this.draft = '';
        this.refreshSuggestions();
        queueMicrotask(() => this.input?.nativeElement.focus());
    }

    stopEditing(): void {
        if (this.draft.trim()) this.commit(this.draft);
        this.editing = false;
        this.draft = '';
    }

    onInput(): void {
        this.refreshSuggestions();
    }

    onKey(event: KeyboardEvent): void {
        if (event.key === 'Enter' || event.key === ',') {
            event.preventDefault();
            this.commit(this.draft);
        } else if (event.key === 'Escape') {
            event.preventDefault();
            this.editing = false;
            this.draft = '';
        } else if (event.key === 'Backspace' && this.draft === '' && this._tags.length) {
            event.preventDefault();
            this.removeAt(this._tags.length - 1);
        }
    }

    pickSuggestion(tag: string): void {
        this.commit(tag);
        queueMicrotask(() => this.input?.nativeElement.focus());
    }

    removeAt(index: number): void {
        if (this.readonly) return;
        const next = [...this._tags];
        next.splice(index, 1);
        this._tags = next;
        this.tagsChange.emit(next);
    }

    private commit(raw: string): void {
        const normalized = raw.trim().toLowerCase();
        if (!normalized || normalized.length > 32) {
            this.draft = '';
            return;
        }
        if (this._tags.includes(normalized)) {
            this.draft = '';
            this.refreshSuggestions();
            return;
        }
        if (this._tags.length >= 16) {
            this.draft = '';
            return;
        }
        const next = [...this._tags, normalized];
        this._tags = next;
        this.draft = '';
        this.refreshSuggestions();
        this.tagsChange.emit(next);
        this.cdr.markForCheck();
    }

    private refreshSuggestions(): void {
        const query = this.draft.trim().toLowerCase();
        const owned = new Set(this._tags);
        const pool = this.vocabulary.filter((t) => !owned.has(t));
        this.suggestions = (query
            ? pool.filter((t) => t.includes(query))
            : pool
        ).slice(0, 6);
        this.cdr.markForCheck();
    }
}
