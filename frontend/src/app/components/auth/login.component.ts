import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-login',
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
  `]
})
export class LoginComponent {
    constructor(private authService: AuthService) { }

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
