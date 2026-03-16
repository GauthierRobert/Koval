import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastSeverity = 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  message: string;
  severity: ToastSeverity;
}

@Injectable({ providedIn: 'root' })
export class ErrorToastService {
  private nextId = 0;
  private readonly maxToasts = 3;
  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  show(message: string, severity: ToastSeverity = 'error', duration = 5000): void {
    const toast: Toast = { id: this.nextId++, message, severity };
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
}
