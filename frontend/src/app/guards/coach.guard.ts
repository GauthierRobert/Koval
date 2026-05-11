import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from '../services/auth.service';
import {catchError, filter, map, take, timeout} from 'rxjs/operators';
import {of} from 'rxjs';

export const coachGuard: CanActivateFn = () => {
    const router = inject(Router);
    const authService = inject(AuthService);

    // Fast path: user already loaded
    if (authService.currentUser !== null) {
        if (authService.isCoach()) return true;
        router.navigate(['/dashboard']);
        return false;
    }

    // Wait for user to be loaded from token (async HTTP call)
    return authService.user$.pipe(
        filter(u => u !== null),
        take(1),
        map(() => {
            if (authService.isCoach()) return true;
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
