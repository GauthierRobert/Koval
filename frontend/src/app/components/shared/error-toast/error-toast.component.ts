import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ErrorToastService} from '../../../services/error-toast.service';

@Component({
  selector: 'app-error-toast',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-toast-container" aria-live="assertive" role="alert">
      <div
        *ngFor="let toast of toasts$ | async"
        class="error-toast"
        [class.error]="toast.severity === 'error'"
        [class.warning]="toast.severity === 'warning'"
        [class.info]="toast.severity === 'info'"
        [class.success]="toast.severity === 'success'"
      >
        <span class="error-toast-message">{{ toast.message }}</span>
        <button class="error-toast-close" (click)="dismiss(toast.id)" aria-label="Close notification">&times;</button>
      </div>
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
        gap: 8px;
        max-width: 420px;
      }

      .error-toast {
        background: var(--surface-elevated, #18182a);
        border: 1px solid var(--border, rgba(255, 255, 255, 0.08));
        border-left: 4px solid #ef4444;
        border-radius: 10px;
        padding: 12px 16px;
        display: flex;
        align-items: center;
        gap: 12px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
        animation: slideUp 0.3s ease-out;
        min-width: 300px;
      }

      .error-toast.warning {
        border-left-color: #f59e0b;
      }

      .error-toast.info {
        border-left-color: var(--primary, #00c2ff);
      }

      .error-toast.success {
        border-left-color: var(--success-color, #34d399);
      }

      .error-toast-message {
        flex: 1;
        color: var(--text-primary, #eeeef8);
        font-size: 13px;
        line-height: 1.4;
      }

      .error-toast-close {
        background: none;
        border: none;
        color: var(--text-muted, #404060);
        font-size: 18px;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
        flex-shrink: 0;
      }

      .error-toast-close:hover {
        color: var(--text-secondary, #8080a0);
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
}
