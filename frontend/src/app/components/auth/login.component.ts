import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {AuthService} from '../../services/auth.service';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './login.component.html',
    styleUrl: './login.component.css',
})
export class LoginComponent {
    devUserId = '';
    devRole: 'ATHLETE' | 'COACH' = 'ATHLETE';

    constructor(private authService: AuthService, private router: Router) { }

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

    devLogin() {
        if (!this.devUserId.trim()) return;
        this.authService.devLogin(this.devUserId.trim(), this.devUserId.trim(), this.devRole).subscribe({
            next: () => this.router.navigate(['/']),
        });
    }
}
