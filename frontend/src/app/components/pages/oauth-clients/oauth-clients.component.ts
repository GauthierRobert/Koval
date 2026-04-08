import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { OAuthClientItem, OAuthClientService } from '../../../services/oauth-client.service';
import { SkillsService, SkillSummary } from '../../../services/skills.service';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../../environments/environment';

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
  private skillsService = inject(SkillsService);

  clients$ = this.oauthClientService.clients$;
  revoking$ = new BehaviorSubject<string | null>(null);

  skills$ = new BehaviorSubject<SkillSummary[]>([]);

  readonly mcpUrl = `${environment.apiUrl}/mcp/sse`;
  readonly isProduction = environment.production;
  readonly skillsZipUrl = this.skillsService.getZipUrl();

  ngOnInit(): void {
    this.oauthClientService.loadClients();
    this.skillsService.getSkills().subscribe({
      next: (skills) => this.skills$.next(skills),
      error: () => this.skills$.next([]),
    });
  }

  revokeClient(client: OAuthClientItem): void {
    this.revoking$.next(client.id);
    this.oauthClientService.revokeClient(client.id).subscribe({
      next: () => this.revoking$.next(null),
      error: () => this.revoking$.next(null),
    });
  }

  skillDownloadUrl(filename: string): string {
    return this.skillsService.getDownloadUrl(filename);
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
