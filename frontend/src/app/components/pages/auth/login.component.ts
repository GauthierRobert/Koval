import {Component, OnInit, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {Subscription} from 'rxjs';
import {AuthService} from '../../../services/auth.service';
import {environment} from '../../../../environments/environment';

type BackendStatus = 'checking' | 'starting' | 'offline' | 'active';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './login.component.html',
    styleUrl: './login.component.css',
})
export class LoginComponent implements OnInit, OnDestroy {
    devUserId = '';
    devRole: 'ATHLETE' | 'COACH' = 'ATHLETE';
    backendStatus: BackendStatus = 'checking';

    private statusSub?: Subscription;
    private retryTimeout?: ReturnType<typeof setTimeout>;
    private startingTimeout?: ReturnType<typeof setTimeout>;

    constructor(private authService: AuthService, private router: Router, private http: HttpClient) { }

    ngOnInit() {
        this.checkBackendStatus();
    }

    ngOnDestroy() {
        this.statusSub?.unsubscribe();
        clearTimeout(this.retryTimeout);
        clearTimeout(this.startingTimeout);
    }

    get statusLabel(): string {
        const labels: Record<BackendStatus, string> = {
            checking: 'Checking server',
            starting: 'Server starting',
            active: 'Active',
            offline: 'Server offline',
        };
        return labels[this.backendStatus];
    }

    private checkBackendStatus() {
        this.startingTimeout = setTimeout(() => {
            if (this.backendStatus === 'checking') {
                this.backendStatus = 'starting';
            }
        }, 1500);

        this.statusSub?.unsubscribe();
        this.statusSub = this.http.get(`${environment.apiUrl}/actuator/health`).subscribe({
            next: () => {
                clearTimeout(this.startingTimeout);
                this.backendStatus = 'active';
            },
            error: (err) => {
                clearTimeout(this.startingTimeout);
                if (err.status > 0) {
                    // Got an HTTP response (e.g. 401/404) — backend is up
                    this.backendStatus = 'active';
                } else {
                    // Network error — no response yet
                    this.backendStatus = 'offline';
                    this.retryTimeout = setTimeout(() => this.checkBackendStatus(), 8000);
                }
            },
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

    devLogin() {
        if (!this.devUserId.trim()) return;
        this.authService.devLogin(this.devUserId.trim(), this.devUserId.trim(), this.devRole).subscribe({
            next: () => this.router.navigate(['/']),
        });
    }
}
