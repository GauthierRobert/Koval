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
            const handler = isGoogle
                ? this.authService.handleGoogleCallback(code)
                : this.authService.handleStravaCallback(code);

            handler.subscribe({
                next: (res) => {
                    if (res.user.needsOnboarding) {
                        this.router.navigate(['/onboarding']);
                    } else {
                        this.router.navigate(['/']);
                    }
                },
                error: () => this.router.navigate(['/login'])
            });
        });
    }
}
