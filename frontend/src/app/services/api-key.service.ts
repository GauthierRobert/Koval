import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ApiKeyListItem {
  id: string;
  prefix: string;
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export interface ApiKeyResponse {
  id: string;
  key: string;
  prefix: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class ApiKeyService {
  private apiUrl = `${environment.apiUrl}/api/auth/api-keys`;
  private readonly http = inject(HttpClient);

  private keysSubject = new BehaviorSubject<ApiKeyListItem[]>([]);
  keys$ = this.keysSubject.asObservable();

  loadKeys(): void {
    this.http.get<ApiKeyListItem[]>(this.apiUrl).subscribe({
      next: (keys) => this.keysSubject.next(keys),
      error: () => this.keysSubject.next([]),
    });
  }

  createKey(name: string): Observable<ApiKeyResponse> {
    return this.http.post<ApiKeyResponse>(this.apiUrl, { name }).pipe(
      tap(() => this.loadKeys()),
    );
  }

  revokeKey(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => this.loadKeys()),
    );
  }
}
