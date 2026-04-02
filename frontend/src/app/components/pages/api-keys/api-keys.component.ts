import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ApiKeyListItem, ApiKeyResponse, ApiKeyService } from '../../../services/api-key.service';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-api-keys',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './api-keys.component.html',
  styleUrl: './api-keys.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiKeysComponent implements OnInit {
  private apiKeyService = inject(ApiKeyService);

  keys$ = this.apiKeyService.keys$;

  newKeyName = '';
  createdKey: ApiKeyResponse | null = null;
  copied = false;
  creating$ = new BehaviorSubject(false);
  revoking$ = new BehaviorSubject<string | null>(null);

  ngOnInit(): void {
    this.apiKeyService.loadKeys();
  }

  createKey(): void {
    if (!this.newKeyName.trim()) return;
    this.creating$.next(true);
    this.apiKeyService.createKey(this.newKeyName.trim()).subscribe({
      next: (response) => {
        this.createdKey = response;
        this.newKeyName = '';
        this.creating$.next(false);
        this.copied = false;
      },
      error: () => this.creating$.next(false),
    });
  }

  copyKey(): void {
    if (!this.createdKey) return;
    navigator.clipboard.writeText(this.createdKey.key).then(() => {
      this.copied = true;
    });
  }

  dismissCreatedKey(): void {
    this.createdKey = null;
    this.copied = false;
  }

  revokeKey(key: ApiKeyListItem): void {
    this.revoking$.next(key.id);
    this.apiKeyService.revokeKey(key.id).subscribe({
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
