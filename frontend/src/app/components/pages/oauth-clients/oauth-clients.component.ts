import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { OAuthClientItem, OAuthClientService } from '../../../services/oauth-client.service';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-oauth-clients',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './oauth-clients.component.html',
  styleUrl: './oauth-clients.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OAuthClientsComponent implements OnInit {
  private oauthClientService = inject(OAuthClientService);

  clients$ = this.oauthClientService.clients$;
  revoking$ = new BehaviorSubject<string | null>(null);

  ngOnInit(): void {
    this.oauthClientService.loadClients();
  }

  revokeClient(client: OAuthClientItem): void {
    this.revoking$.next(client.id);
    this.oauthClientService.revokeClient(client.id).subscribe({
      next: () => this.revoking$.next(null),
      error: () => this.revoking$.next(null),
    });
  }

  formatDate(date: string | null): string {
    if (!date) return '-';
    return new Date(date).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }
}
