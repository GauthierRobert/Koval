import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';

export const authGuard: CanActivateFn = (_route, state) => {
    const router = inject(Router);
    const token = localStorage.getItem('token');

    if (token) {
        return true;
    }

    if (state.url !== '/login') {
        localStorage.setItem('post_login_redirect', state.url);
    }
    router.navigate(['/login']);
    return false;
};
