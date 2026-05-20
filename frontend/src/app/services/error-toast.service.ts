import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export type ToastSeverity = 'error' | 'warning' | 'info' | 'success';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface Toast {
  id: number;
  message: string;
  severity: ToastSeverity;
  action?: ToastAction;
}

@Injectable({ providedIn: 'root' })
export class ErrorToastService {
  private nextId = 0;
  private readonly maxToasts = 3;
  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  show(message: string, severity: ToastSeverity = 'error', duration = 5000): void {
    this.push({ message, severity }, duration);
  }

  /**
   * Show a toast with a single inline action (e.g. "Undo"). The action invokes the supplied
   * callback then dismisses the toast.
   */
  showWithAction(
    message: string,
    action: ToastAction,
    severity: ToastSeverity = 'success',
    duration = 8000,
  ): void {
    this.push({ message, severity, action }, duration);
  }

  private push(partial: Omit<Toast, 'id'>, duration: number): void {
    const toast: Toast = { id: this.nextId++, ...partial };
    const current = this.toastsSubject.value;

    // Keep only the most recent toasts
    const updated = [...current, toast].slice(-this.maxToasts);
    this.toastsSubject.next(updated);

    setTimeout(() => this.dismiss(toast.id), duration);
  }

  dismiss(id: number): void {
    const updated = this.toastsSubject.value.filter((t) => t.id !== id);
    this.toastsSubject.next(updated);
  }

  /** Invoke a toast's action (if any) and dismiss it. */
  runAction(id: number): void {
    const toast = this.toastsSubject.value.find((t) => t.id === id);
    if (toast?.action) {
      toast.action.onClick();
    }
    this.dismiss(id);
  }
}
