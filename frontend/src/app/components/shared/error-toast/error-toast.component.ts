import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {AsyncPipe} from '@angular/common';
import {ErrorToastService} from '../../../services/error-toast.service';

@Component({
  selector: 'app-error-toast',
  standalone: true,
  imports: [AsyncPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-toast-container" aria-live="assertive" role="alert">
      @for (toast of toasts$ | async; track toast.id) {
        <div
          class="error-toast"
          [class.error]="toast.severity === 'error'"
          [class.warning]="toast.severity === 'warning'"
          [class.info]="toast.severity === 'info'"
          [class.success]="toast.severity === 'success'"
        >
          <span class="error-toast-message">{{ toast.message }}</span>
          @if (toast.action) {
            <button
              class="error-toast-action"
              type="button"
              (click)="runAction(toast.id)"
            >
              {{ toast.action.label }}
            </button>
          }
          <button class="error-toast-close" (click)="dismiss(toast.id)" aria-label="Close notification">&times;</button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .error-toast-container {
        position: fixed;
        bottom: 24px;
        right: 24px;
        z-index: 10000;
        display: flex;
        flex-direction: column;
        gap: var(--space-sm);
        max-width: 420px;
      }

      .error-toast {
        background: var(--surface-elevated);
        border: 1px solid var(--glass-border);
        border-left: 4px solid var(--danger-color);
        border-radius: var(--radius-md);
        padding: 12px 16px;
        display: flex;
        align-items: center;
        gap: 12px;
        box-shadow: var(--shadow-lg);
        animation: slideUp 0.3s ease-out;
        min-width: 300px;
      }

      .error-toast.warning {
        border-left-color: var(--dev-accent-color);
      }

      .error-toast.info {
        border-left-color: var(--info-color);
      }

      .error-toast.success {
        border-left-color: var(--success-color);
      }

      .error-toast-message {
        flex: 1;
        color: var(--text-color);
        font-size: 13px;
        line-height: 1.4;
      }

      .error-toast-close {
        background: none;
        border: none;
        color: var(--text-muted);
        font-size: 18px;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
        flex-shrink: 0;
      }

      .error-toast-close:hover {
        color: var(--text-color);
      }

      .error-toast-action {
        background: transparent;
        border: 1px solid var(--glass-border);
        border-radius: var(--radius-sm);
        color: var(--text-color);
        font-size: 12px;
        font-weight: 700;
        cursor: pointer;
        padding: 4px 10px;
        flex-shrink: 0;
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }

      .error-toast-action:hover {
        background: var(--accent-subtle);
        color: var(--accent-color);
        border-color: var(--accent-color);
      }

      @keyframes slideUp {
        from {
          transform: translateY(100%);
          opacity: 0;
        }
        to {
          transform: translateY(0);
          opacity: 1;
        }
      }
    `,
  ],
})
export class ErrorToastComponent {
  private readonly errorToastService = inject(ErrorToastService);
  readonly toasts$ = this.errorToastService.toasts$;

  dismiss(id: number): void {
    this.errorToastService.dismiss(id);
  }

  runAction(id: number): void {
    this.errorToastService.runAction(id);
  }
}
