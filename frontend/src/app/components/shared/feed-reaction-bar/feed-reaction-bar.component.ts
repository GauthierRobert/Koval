import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ALLOWED_REACTION_EMOJI, ReactionEmoji } from '../../../models/club.model';

const EMOJI_GLYPH: Record<ReactionEmoji, string> = {
  fire: '\u{1F525}',
  muscle: '\u{1F4AA}',
  clap: '\u{1F44F}',
  heart: '\u{2764}\u{FE0F}',
  party: '\u{1F389}',
  raise: '\u{1F64C}',
};

interface ReactionRow {
  emoji: ReactionEmoji;
  glyph: string;
  count: number;
  active: boolean;
}

@Component({
  selector: 'app-feed-reaction-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="reaction-bar" data-testid="reaction-bar" (click)="$event.stopPropagation()">
      @for (r of rows(); track r.emoji) {
        @if (r.count > 0 || pickerOpen()) {
          <button
            type="button"
            class="reaction-chip"
            [attr.data-testid]="'reaction-chip-' + r.emoji"
            [class.active]="r.active"
            (click)="onToggle(r.emoji)"
            [attr.aria-pressed]="r.active"
            [attr.aria-label]="'react with ' + r.emoji"
          >
            <span class="emoji">{{ r.glyph }}</span>
            @if (r.count > 0) {
              <span data-testid="reaction-chip-count" class="count">{{ r.count }}</span>
            }
          </button>
        }
      }
      <button
        type="button"
        class="reaction-add"
        data-testid="reaction-add"
        (click)="togglePicker()"
        [attr.aria-label]="'add reaction'"
      >
        @if (!pickerOpen()) {
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="12" cy="12" r="10" />
            <path d="M8 14s1.5 2 4 2 4-2 4-2" />
            <line x1="9" y1="9" x2="9.01" y2="9" />
            <line x1="15" y1="9" x2="15.01" y2="9" />
            <line x1="19" y1="3" x2="19" y2="7" />
            <line x1="17" y1="5" x2="21" y2="5" />
          </svg>
        } @else {
          ×
        }
      </button>
    </div>
  `,
  styles: `
    .reaction-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px;
      margin-top: var(--space-xs);
    }
    .reaction-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 8px;
      border-radius: 999px;
      border: none;
      background: var(--surface-elevated);
      color: var(--text-color);
      font-size: var(--text-xs);
      cursor: pointer;
      line-height: 1;
      min-height: 22px;
    }
    .reaction-chip:hover {
      background: var(--glass-bg);
    }
    .reaction-chip.active {
      background: var(--accent-subtle);
      color: var(--accent-color);
    }
    .reaction-chip .emoji {
      font-size: 13px;
    }
    .reaction-chip .count {
      font-size: 10px;
      font-variant-numeric: tabular-nums;
      opacity: 0.8;
    }
    .reaction-add {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      border: 1px dashed var(--glass-border);
      background: transparent;
      color: var(--text-muted);
      cursor: pointer;
    }
    .reaction-add:hover {
      color: var(--accent-color);
      border-color: var(--accent-color);
    }
  `,
})
export class FeedReactionBarComponent {
  readonly reactions = input<{ [emoji: string]: string[] } | undefined>(undefined);
  readonly currentUserId = input<string | null>(null);
  readonly toggled = output<ReactionEmoji>();

  readonly pickerOpen = signal(false);

  readonly rows = computed<ReactionRow[]>(() => {
    const map = this.reactions() ?? {};
    const userId = this.currentUserId();
    return ALLOWED_REACTION_EMOJI.map((emoji) => {
      const users = map[emoji] ?? [];
      return {
        emoji,
        glyph: EMOJI_GLYPH[emoji],
        count: users.length,
        active: userId != null && users.includes(userId),
      };
    });
  });

  togglePicker(): void {
    this.pickerOpen.update((open) => !open);
  }

  onToggle(emoji: ReactionEmoji): void {
    this.toggled.emit(emoji);
    this.pickerOpen.set(false);
  }
}
