import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-auth-callback',
    template: `<p>Authenticating...</p>`
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
            if (code) {
                this.authService.handleStravaCallback(code).subscribe({
                    next: () => this.router.navigate(['/']),
                    error: () => this.router.navigate(['/login'])
                });
            } else {
                this.router.navigate(['/login']);
            }
        });
    }
}
