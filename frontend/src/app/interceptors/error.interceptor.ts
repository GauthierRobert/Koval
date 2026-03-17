import {HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {catchError, throwError} from 'rxjs';
import {ErrorToastService, ToastSeverity} from '../services/error-toast.service';
import {ErrorResponse} from '../models/error-response.model';

const FALLBACK_MESSAGES: Record<number, string> = {
  0: 'Unable to reach the server. Please check your connection.',
  403: "You don't have permission to perform this action.",
  404: 'The requested resource was not found.',
  429: 'Too many requests. Please wait a moment and try again.',
  500: 'Something went wrong on the server. Please try again later.',
  502: 'The server is temporarily unavailable. Please try again later.',
  503: 'The service is temporarily unavailable. Please try again later.',
};

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const errorToastService = inject(ErrorToastService);

  return next(req).pipe(
    catchError((error) => {
      // Skip 401 — handled by auth interceptor
      if (error.status === 401) {
        return throwError(() => error);
      }

      // Try to extract message from backend ErrorResponse
      let message: string;
      const body = error.error as ErrorResponse | null;
      if (body && typeof body === 'object' && body.message) {
        message = body.message;
      } else {
        message = FALLBACK_MESSAGES[error.status] || `An unexpected error occurred (${error.status}).`;
      }

      const severity: ToastSeverity = error.status >= 500 ? 'error' : 'warning';
      errorToastService.show(message, severity);

      return throwError(() => error);
    })
  );
};
