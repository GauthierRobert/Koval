import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { SuuntoSyncService } from '../../../services/suunto-sync.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-suunto-callback',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './auth-callback.component.html',
  styleUrl: './auth-callback.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuuntoCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private suuntoSync = inject(SuuntoSyncService);
  private authService = inject(AuthService);

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const code = params['code'];
      const error = params['error'];
      const isLogin = params['state'] === 'login';

      if (error || !code) {
        if (isLogin) {
          this.router.navigate(['/login'], {
            queryParams: { suuntoError: error ?? 'Suunto authorization was cancelled' },
          });
        } else {
          this.notifyOpener(false, error ?? 'Suunto authorization was cancelled');
        }
        return;
      }

      if (isLogin) {
        this.authService.handleSuuntoCallback(code).subscribe({
          next: (res) => {
            if (res.user.needsOnboarding) {
              this.router.navigate(['/onboarding']);
              return;
            }
            this.router.navigate(['/']);
          },
          error: (err) =>
            this.router.navigate(['/login'], {
              queryParams: { suuntoError: err?.error?.error ?? 'Suunto login failed' },
            }),
        });
        return;
      }

      this.suuntoSync.completeOAuth(code).subscribe({
        next: () => this.notifyOpener(true),
        error: (err) => this.notifyOpener(false, err?.error?.message ?? 'Suunto linking failed'),
      });
    });
  }

  private notifyOpener(success: boolean, message?: string): void {
    if (window.opener) {
      window.opener.postMessage(
        { type: 'ACCOUNT_LINKED', success, ...(message ? { error: message } : {}) },
        window.location.origin,
      );
      window.close();
    } else {
      this.router.navigate(['/'], { queryParams: success ? {} : { suuntoError: message } });
    }
  }
}
