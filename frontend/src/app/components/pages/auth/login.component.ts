import {ChangeDetectionStrategy, Component, OnInit, inject} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import {TranslateModule} from '@ngx-translate/core';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [TranslateModule],
    templateUrl: './login.component.html',
    styleUrl: './login.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent implements OnInit {
    private authService = inject(AuthService);
    private route = inject(ActivatedRoute);

    ngOnInit(): void {
        this.route.queryParams.subscribe(params => {
            const returnTo = params['returnTo'];
            if (returnTo) {
                localStorage.setItem('oauth_return_to', returnTo);
            }
        });
    }

    loginWithStrava() {
        this.authService.getStravaAuthUrl().subscribe(res => {
            window.location.href = res.authUrl;
        });
    }

    loginWithGoogle() {
        this.authService.getGoogleAuthUrl().subscribe(res => {
            window.location.href = res.authUrl;
        });
    }
}
