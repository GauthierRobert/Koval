import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PolarSyncService } from '../../../services/polar-sync.service';

@Component({
  selector: 'app-polar-callback',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './auth-callback.component.html',
  styleUrl: './auth-callback.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PolarCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private polarSync = inject(PolarSyncService);

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const code = params['code'];
      const error = params['error'];

      if (error || !code) {
        this.notifyOpener(false, error ?? 'Polar authorization was cancelled');
        return;
      }

      this.polarSync.completeOAuth(code).subscribe({
        next: () => this.notifyOpener(true),
        error: (err) => this.notifyOpener(false, err?.error?.message ?? 'Polar linking failed'),
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
      this.router.navigate(['/'], { queryParams: success ? {} : { polarError: message } });
    }
  }
}
