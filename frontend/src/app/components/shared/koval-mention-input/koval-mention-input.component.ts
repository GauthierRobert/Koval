import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  Output,
  ViewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject, Subscription} from 'rxjs';
import {debounceTime, switchMap} from 'rxjs/operators';
import {ClubFeedService} from '../../../services/club-feed.service';
import {MentionSuggestion} from '../../../models/club.model';

@Component({
  selector: 'app-koval-mention-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mention-input-wrap" (click)="$event.stopPropagation()">
      @if (multiline) {
        <textarea
          #inputEl
          class="mention-input"
          [rows]="rows"
          [placeholder]="placeholder"
          [(ngModel)]="text"
          (ngModelChange)="onTextChange()"
          (keydown)="onKeyDown($event)"
        ></textarea>
      } @else {
        <input
          #inputEl
          type="text"
          class="mention-input"
          [placeholder]="placeholder"
          [(ngModel)]="text"
          (ngModelChange)="onTextChange()"
          (keydown)="onKeyDown($event)"
        />
      }

      @if (suggestionsOpen && suggestions.length > 0) {
        <div class="mention-dropdown">
          @for (s of suggestions; track s.userId; let i = $index) {
            <button
              type="button"
              class="mention-suggestion"
              [class.active]="i === activeIndex"
              (click)="pickSuggestion(s)">
              <span class="avatar">
                @if (s.profilePicture) {
                  <img [src]="s.profilePicture" [alt]="s.displayName" />
                } @else {
                  {{ initial(s.displayName) }}
                }
              </span>
              <span class="name">{{ s.displayName }}</span>
              <span class="role">{{ s.role }}</span>
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .mention-input-wrap { position: relative; flex: 1; min-width: 0; }
    .mention-input {
      width: 100%;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
      padding: 6px 10px;
      font-size: var(--text-xs);
      color: var(--text-color);
      outline: none;
      box-sizing: border-box;
      font-family: inherit;
    }
    .mention-input::placeholder { color: var(--text-muted); }
    .mention-input:focus { border-color: var(--primary); }
    textarea.mention-input { resize: vertical; min-height: 40px; }
    .mention-dropdown {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      margin-top: 4px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-sm);
      box-shadow: 0 4px 12px rgba(0,0,0,0.4);
      z-index: 100;
      max-height: 220px;
      overflow-y: auto;
    }
    .mention-suggestion {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      padding: 6px 10px;
      background: none;
      border: none;
      cursor: pointer;
      color: var(--text-color);
      font-size: var(--text-xs);
      text-align: left;
    }
    .mention-suggestion:hover, .mention-suggestion.active { background: var(--glass-bg); }
    .mention-suggestion .avatar {
      width: 22px; height: 22px; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      background: var(--surface-elevated);
      font-size: 10px; font-weight: 600; color: var(--text-muted);
      overflow: hidden;
      flex-shrink: 0;
    }
    .mention-suggestion .avatar img { width: 100%; height: 100%; object-fit: cover; }
    .mention-suggestion .name { flex: 1; font-weight: 500; }
    .mention-suggestion .role { font-size: 9px; color: var(--text-muted); text-transform: uppercase; }
  `,
})
export class KovalMentionInputComponent implements OnDestroy {
  @Input({required: true}) clubId!: string;
  @Input() placeholder = '';
  @Input() multiline = false;
  @Input() rows = 3;
  @Input() set value(v: string) {
    if (v !== this.text) {
      this.text = v ?? '';
    }
  }
  get value(): string {
    return this.text;
  }

  /** Resets the input to empty (e.g. after submit). */
  @Input() set resetSignal(_: number) {
    this.text = '';
    this.confirmedMentions.clear();
    this.suggestionsOpen = false;
    this.emitMentions();
  }

  @Output() textChange = new EventEmitter<string>();
  @Output() mentionsChange = new EventEmitter<string[]>();
  @Output() submitted = new EventEmitter<void>();

  @ViewChild('inputEl') inputEl?: ElementRef<HTMLInputElement | HTMLTextAreaElement>;

  private feedService = inject(ClubFeedService);

  text = '';
  suggestions: MentionSuggestion[] = [];
  suggestionsOpen = false;
  activeIndex = 0;

  private confirmedMentions = new Map<string, string>(); // userId -> displayName
  private query$ = new Subject<string>();
  private querySub: Subscription;
  private mentionStart = -1;

  constructor() {
    this.querySub = this.query$
      .pipe(
        debounceTime(150),
        switchMap((q) => this.feedService.suggestMentions(this.clubId, q)),
      )
      .subscribe((list) => {
        this.suggestions = list;
        this.activeIndex = 0;
        this.suggestionsOpen = list.length > 0 && this.mentionStart >= 0;
      });
  }

  ngOnDestroy(): void {
    this.querySub.unsubscribe();
  }

  onTextChange(): void {
    this.textChange.emit(this.text);
    this.emitMentions();
    this.detectMentionTrigger();
  }

  onKeyDown(ev: KeyboardEvent): void {
    if (this.suggestionsOpen && this.suggestions.length > 0) {
      if (ev.key === 'ArrowDown') {
        ev.preventDefault();
        this.activeIndex = (this.activeIndex + 1) % this.suggestions.length;
        return;
      }
      if (ev.key === 'ArrowUp') {
        ev.preventDefault();
        this.activeIndex = (this.activeIndex - 1 + this.suggestions.length) % this.suggestions.length;
        return;
      }
      if (ev.key === 'Enter' || ev.key === 'Tab') {
        ev.preventDefault();
        this.pickSuggestion(this.suggestions[this.activeIndex]);
        return;
      }
      if (ev.key === 'Escape') {
        this.suggestionsOpen = false;
        return;
      }
    }
    if (ev.key === 'Enter' && !this.multiline && !ev.shiftKey) {
      ev.preventDefault();
      this.submitted.emit();
    }
  }

  initial(name: string): string {
    return (name ?? '').charAt(0).toUpperCase();
  }

  pickSuggestion(s: MentionSuggestion): void {
    if (this.mentionStart < 0 || !s.displayName) return;
    const before = this.text.slice(0, this.mentionStart);
    const after = this.text.slice(this.cursorPos());
    this.text = `${before}@${s.displayName} ${after}`;
    this.confirmedMentions.set(s.userId, s.displayName);
    this.suggestionsOpen = false;
    this.mentionStart = -1;
    this.textChange.emit(this.text);
    this.emitMentions();

    // Move cursor to right after the inserted mention (+ space)
    setTimeout(() => {
      const el = this.inputEl?.nativeElement;
      if (!el) return;
      const pos = before.length + s.displayName.length + 2; // @ + name + space
      el.focus();
      try {
        el.setSelectionRange(pos, pos);
      } catch {
        // ignore on textarea fallback
      }
    });
  }

  private cursorPos(): number {
    return this.inputEl?.nativeElement.selectionStart ?? this.text.length;
  }

  private detectMentionTrigger(): void {
    const pos = this.cursorPos();
    const slice = this.text.slice(0, pos);
    // Find the last '@' that's at the start of the text or preceded by whitespace,
    // and not followed by whitespace before the cursor.
    const m = /(?:^|\s)@([^\s@]{0,40})$/.exec(slice);
    if (m) {
      this.mentionStart = pos - m[1].length - 1; // position of the '@'
      this.query$.next(m[1]);
    } else {
      this.mentionStart = -1;
      this.suggestionsOpen = false;
    }
  }

  private emitMentions(): void {
    // Only keep mentions whose display name still appears in the text.
    const remaining: string[] = [];
    for (const [userId, displayName] of this.confirmedMentions) {
      if (this.text.includes('@' + displayName)) {
        remaining.push(userId);
      }
    }
    // Drop any pruned ones from the map for next render.
    for (const userId of Array.from(this.confirmedMentions.keys())) {
      if (!remaining.includes(userId)) this.confirmedMentions.delete(userId);
    }
    this.mentionsChange.emit(remaining);
  }
}
