import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface OAuthClientItem {
  id: string;
  clientName: string;
  clientIdPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class OAuthClientService {
  private apiUrl = `${environment.apiUrl}/api/oauth/clients`;
  private readonly http = inject(HttpClient);

  private clientsSubject = new BehaviorSubject<OAuthClientItem[]>([]);
  clients$ = this.clientsSubject.asObservable();

  loadClients(): void {
    this.http.get<OAuthClientItem[]>(this.apiUrl).subscribe({
      next: (clients) => this.clientsSubject.next(clients),
      error: () => this.clientsSubject.next([]),
    });
  }

  revokeClient(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.loadClients()));
  }
}
