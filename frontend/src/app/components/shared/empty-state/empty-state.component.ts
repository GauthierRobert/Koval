import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Standardised empty / null-state surface.
 *
 * Wraps an icon (projected via `<svg>` content), a title, an optional caption,
 * and an optional CTA slot in a glass panel that respects the design tokens.
 * Use anywhere a list, table, or chart can render zero items.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty" role="status" aria-live="polite">
      @if (icon()) {
        <div class="empty__icon" aria-hidden="true">
          <ng-content select="[icon]" />
        </div>
      }
      <h3 class="empty__title">{{ title() }}</h3>
      @if (caption(); as c) {
        <p class="empty__caption">{{ c }}</p>
      }
      <div class="empty__cta">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-sm);
        text-align: center;
        padding: var(--space-2xl) var(--space-lg);
        background: var(--surface-elevated);
        border: 1px solid var(--glass-border);
        border-radius: var(--radius-lg);
        color: var(--text-muted);
      }
      .empty__icon {
        color: var(--text-dim);
        margin-bottom: var(--space-xs);
      }
      .empty__title {
        margin: 0;
        color: var(--text-color);
        font-family: var(--font-display);
        font-size: var(--text-lg);
        font-weight: 700;
      }
      .empty__caption {
        margin: 0;
        font-size: var(--text-sm);
        color: var(--text-muted);
        max-width: 42ch;
      }
      .empty__cta:empty {
        display: none;
      }
      .empty__cta {
        margin-top: var(--space-sm);
      }
    `,
  ],
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly caption = input<string | undefined>(undefined);
  readonly icon = input<boolean>(true);
}
