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
      color: #fff; 
    }
    button {
      padding: 1rem 2rem;
      background: #fc4c02; /* Strava Orange */
      color: white;
      border: none;
      border-radius: 4px;
      font-size: 1.2rem;
      cursor: pointer;
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
