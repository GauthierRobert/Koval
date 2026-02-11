import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-login',
    template: `
    <div class="login-container">
      <h1>Welcome to Training Planner AI</h1>
      <button (click)="loginWithStrava()">Connect with Strava</button>
    </div>
  `,
    styles: [`
    .login-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100vh;
      color: var(--text-color);
      gap: 2rem;
    }
    h1 {
      font-weight: 800;
      letter-spacing: -1px;
    }
    button {
      padding: 1rem 2rem;
      background: #fc4c02;
      color: white;
      border: none;
      border-radius: 12px;
      font-size: 1.1rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
    }
    button:hover {
      filter: brightness(1.15);
      transform: translateY(-1px);
    }
  `]
})
export class LoginComponent implements OnInit {
    constructor(private authService: AuthService) { }

    ngOnInit(): void { }

    loginWithStrava() {
        this.authService.getStravaAuthUrl().subscribe(res => {
            window.location.href = res.authUrl;
        });
    }
}
