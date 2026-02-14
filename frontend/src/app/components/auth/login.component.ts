import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo">
          <div class="logo-icon">
            <div class="logo-inner"></div>
          </div>
          <h1>Koval <span class="accent">TRAINING</span></h1>
        </div>
        <p class="subtitle">AI-powered cycling & triathlon training</p>
        <div class="auth-buttons">
          <button class="auth-btn strava" (click)="loginWithStrava()">
            <span class="btn-text">Connect with Strava</span>
          </button>
          <button class="auth-btn google" (click)="loginWithGoogle()">
            <span class="btn-text">Continue with Google</span>
          </button>
        </div>

        <!-- DEV ONLY -->
        <div class="dev-section">
          <div class="dev-label">DEV LOGIN</div>
          <div class="dev-row">
            <input
              class="dev-input"
              [(ngModel)]="devUserId"
              placeholder="User ID"
              (keydown.enter)="devLogin()"
            />
            <select class="dev-select" [(ngModel)]="devRole">
              <option value="ATHLETE">Athlete</option>
              <option value="COACH">Coach</option>
            </select>
            <button class="dev-btn" (click)="devLogin()" [disabled]="!devUserId.trim()">GO</button>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .login-container {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100vh;
      background: var(--bg-color, #0a0a0c);
    }

    .login-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 24px;
      padding: 48px;
      background: rgba(22, 22, 28, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 24px;
      backdrop-filter: blur(20px);
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .logo-icon {
      width: 36px;
      height: 36px;
      background: var(--accent-color, #ff6600);
      transform: rotate(-10deg);
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
    }

    .logo-inner {
      width: 18px;
      height: 18px;
      background: white;
      clip-path: polygon(50% 0%, 100% 100%, 0% 100%);
    }

    h1 {
      font-weight: 800;
      font-size: 20px;
      letter-spacing: 2px;
      color: var(--text-color, #e0e0e0);
      margin: 0;
    }

    h1 .accent {
      color: var(--accent-color, #ff6600);
      font-weight: 400;
    }

    .subtitle {
      font-size: 13px;
      color: var(--text-muted, #888);
      margin: 0;
    }

    .auth-buttons {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      margin-top: 8px;
    }

    .auth-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 14px 32px;
      border: none;
      border-radius: 12px;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
      width: 100%;
    }

    .auth-btn:hover {
      filter: brightness(1.15);
      transform: translateY(-1px);
    }

    .auth-btn.strava {
      background: #fc4c02;
      color: white;
    }

    .auth-btn.google {
      background: white;
      color: #333;
    }

    /* DEV ONLY */
    .dev-section {
      width: 100%;
      padding-top: 20px;
      border-top: 1px dashed rgba(255, 255, 255, 0.1);
    }

    .dev-label {
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 2px;
      color: #f59e0b;
      margin-bottom: 10px;
      text-align: center;
    }

    .dev-row {
      display: flex;
      gap: 8px;
    }

    .dev-input {
      flex: 1;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 8px;
      padding: 10px 12px;
      color: #e0e0e0;
      font-size: 13px;
      font-family: inherit;
      outline: none;
    }

    .dev-input:focus {
      border-color: #f59e0b;
    }

    .dev-select {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 8px;
      padding: 10px 8px;
      color: #e0e0e0;
      font-size: 11px;
      font-weight: 700;
      font-family: inherit;
      cursor: pointer;
      outline: none;
    }

    .dev-select option {
      background: #1a1a1e;
    }

    .dev-btn {
      background: #f59e0b;
      color: #000;
      border: none;
      border-radius: 8px;
      padding: 10px 16px;
      font-size: 11px;
      font-weight: 800;
      cursor: pointer;
      transition: filter 0.2s;
    }

    .dev-btn:hover { filter: brightness(1.15); }
    .dev-btn:disabled { opacity: 0.4; cursor: not-allowed; }
  `]
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
