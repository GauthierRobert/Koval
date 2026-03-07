import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { filter, map, take, timeout, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

export const coachGuard: CanActivateFn = () => {
    const router = inject(Router);
    const authService = inject(AuthService);

    // Fast path: user already loaded
    const current = authService.currentUser;
    if (current !== null) {
        if (current.role === 'COACH') return true;
        router.navigate(['/dashboard']);
        return false;
    }

    // Wait for user to be loaded from token (async HTTP call)
    return authService.user$.pipe(
        filter(u => u !== null),
        take(1),
        map(u => {
            if (u?.role === 'COACH') return true;
            router.navigate(['/dashboard']);
            return false;
        }),
        timeout(5000),
        catchError(() => {
            router.navigate(['/dashboard']);
            return of(false);
        })
    );
};
