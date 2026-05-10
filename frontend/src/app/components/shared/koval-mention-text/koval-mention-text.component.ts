import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MentionRef} from '../../../models/club.model';

interface Segment {
  kind: 'text' | 'mention';
  text: string;
  userId?: string;
}

@Component({
  selector: 'app-koval-mention-text',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="mention-text">
      @for (s of segments; track $index) {
        @if (s.kind === 'mention') {
          <span class="mention" [attr.data-user-id]="s.userId">{{ s.text }}</span>
        } @else {
          <span>{{ s.text }}</span>
        }
      }
    </span>
  `,
  styles: `
    .mention-text { white-space: pre-wrap; word-break: break-word; }
    .mention {
      color: var(--accent-color);
      background: var(--accent-subtle);
      padding: 0 4px;
      border-radius: var(--radius-sm);
      font-weight: 500;
    }
  `,
})
export class KovalMentionTextComponent {
  @Input({required: true}) text = '';
  @Input() mentions: MentionRef[] = [];

  /**
   * Segments the text by detecting @displayName tokens for each known mention.
   * Falls back to plain text when no mentions are present.
   */
  get segments(): Segment[] {
    const text = this.text ?? '';
    if (!this.mentions || this.mentions.length === 0) {
      return [{kind: 'text', text}];
    }

    // Build a regex matching all mention display names; longest first to avoid
    // matching prefixes ("@A" before "@Alice").
    const escaped = [...this.mentions]
      .map((m) => m.displayName ?? '')
      .filter((d) => d.length > 0)
      .sort((a, b) => b.length - a.length)
      .map((d) => d.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));

    if (escaped.length === 0) return [{kind: 'text', text}];

    const re = new RegExp(`@(${escaped.join('|')})`, 'g');
    const out: Segment[] = [];
    let cursor = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      if (m.index > cursor) {
        out.push({kind: 'text', text: text.slice(cursor, m.index)});
      }
      const matched = this.mentions.find((mr) => mr.displayName === m![1]);
      out.push({
        kind: 'mention',
        text: '@' + m[1],
        userId: matched?.userId,
      });
      cursor = m.index + m[0].length;
    }
    if (cursor < text.length) {
      out.push({kind: 'text', text: text.slice(cursor)});
    }
    return out.length > 0 ? out : [{kind: 'text', text}];
  }
}
