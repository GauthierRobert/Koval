import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { BehaviorSubject, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from '../../../../services/auth.service';
import { environment } from '../../../../../environments/environment';

interface ConsentView {
  state: 'loading' | 'consent' | 'redirecting' | 'error';
  clientName?: string;
}

@Component({
  selector: 'app-oauth-consent',
  standalone: true,
  imports: [TranslateModule, AsyncPipe],
  templateUrl: './oauth-consent.component.html',
  styleUrl: './oauth-consent.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OauthConsentComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  private readonly viewSubject = new BehaviorSubject<ConsentView>({ state: 'loading' });
  readonly view$ = this.viewSubject.asObservable();

  private params: Record<string, string> = {};

  ngOnInit(): void {
    const snapshot = this.route.snapshot.queryParams;
    const clientId = snapshot['client_id'];
    const redirectUri = snapshot['redirect_uri'];

    if (!clientId || !redirectUri || snapshot['response_type'] !== 'code') {
      this.viewSubject.next({ state: 'error' });
      return;
    }

    this.params = {
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
    };
    if (snapshot['code_challenge']) this.params['code_challenge'] = snapshot['code_challenge'];
    if (snapshot['code_challenge_method'])
      this.params['code_challenge_method'] = snapshot['code_challenge_method'];
    if (snapshot['state']) this.params['state'] = snapshot['state'];

    // Not logged in: send through normal login, then return here to approve.
    if (!localStorage.getItem('token')) {
      const returnTo = window.location.origin + this.router.url; // full consent URL incl. query
      localStorage.setItem('oauth_return_to', returnTo);
      this.router.navigate(['/login']);
      return;
    }

    this.authService
      .getOAuthClientInfo(clientId)
      .pipe(
        map((info) => info.clientName),
        catchError(() => of(undefined)),
      )
      .subscribe((clientName) => this.viewSubject.next({ state: 'consent', clientName }));
  }

  connect(): void {
    const token = localStorage.getItem('token') ?? '';
    const query = new URLSearchParams({ ...this.params, token }).toString();
    this.viewSubject.next({
      state: 'redirecting',
      clientName: this.viewSubject.value.clientName,
    });
    // Backend issues the authorization code and 302s to the AI client's redirect_uri.
    window.location.href = `${environment.apiUrl}/oauth/authorize?${query}`;
  }

  cancel(): void {
    this.router.navigate(['/dashboard']);
  }
}
