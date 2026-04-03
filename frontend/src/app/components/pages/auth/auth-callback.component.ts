import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import {TranslateModule} from '@ngx-translate/core';

@Component({
    selector: 'app-auth-callback',
    standalone: true,
    imports: [TranslateModule],
    templateUrl: './auth-callback.component.html',
    styleUrl: './auth-callback.component.css'
})
export class AuthCallbackComponent implements OnInit {
    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private authService: AuthService
    ) { }

    ngOnInit() {
        this.route.queryParams.subscribe(params => {
            const code = params['code'];
            if (!code) {
                this.router.navigate(['/login']);
                return;
            }

            const isGoogle = this.router.url.startsWith('/auth/google');
            const isLinking = params['state'] === 'link';

            if (isLinking) {
                const linkHandler = isGoogle
                    ? this.authService.linkGoogleWithCode(code)
                    : this.authService.linkStravaWithCode(code);

                linkHandler.subscribe({
                    next: () => {
                        window.opener?.postMessage({ type: 'ACCOUNT_LINKED', success: true }, window.location.origin);
                        window.close();
                    },
                    error: (err) => {
                        window.opener?.postMessage({ type: 'ACCOUNT_LINKED', success: false, error: err?.error?.message ?? 'Linking failed' }, window.location.origin);
                        window.close();
                    },
                });
                return;
            }

            const handler = isGoogle
                ? this.authService.handleGoogleCallback(code)
                : this.authService.handleStravaCallback(code);

            handler.subscribe({
                next: (res) => {
                    if (res.user.needsOnboarding) {
                        this.router.navigate(['/onboarding']);
                        return;
                    }

                    const returnTo = localStorage.getItem('oauth_return_to');
                    if (returnTo) {
                        localStorage.removeItem('oauth_return_to');
                        const token = localStorage.getItem('auth_token');
                        const separator = returnTo.includes('?') ? '&' : '?';
                        window.location.href = returnTo + separator + 'token=' + encodeURIComponent(token || '');
                        return;
                    }

                    this.router.navigate(['/']);
                },
                error: () => this.router.navigate(['/login'])
            });
        });
    }
}
