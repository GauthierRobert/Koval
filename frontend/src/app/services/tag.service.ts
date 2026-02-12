import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export type TagVisibility = 'PUBLIC' | 'PRIVATE';

export interface Tag {
    id: string;
    name: string;
    displayName: string;
    visibility: TagVisibility;
    createdBy: string;
    createdAt: string;
}

@Injectable({
    providedIn: 'root',
})
export class TagService {
    private apiUrl = 'http://localhost:8080/api/tags';
    private http = inject(HttpClient);
    private authService = inject(AuthService);

    private getUserId(): string {
        let userId = 'mock-user-123';
        const sub = this.authService.user$.subscribe((user) => {
            if (user) userId = user.id;
        });
        sub.unsubscribe();
        return userId;
    }

    getTags(): Observable<Tag[]> {
        return this.http.get<Tag[]>(this.apiUrl, {
            headers: { 'X-User-Id': this.getUserId() },
        });
    }

    createTag(name: string, visibility: TagVisibility = 'PUBLIC'): Observable<Tag> {
        return this.http.post<Tag>(
            this.apiUrl,
            { name, visibility },
            { headers: { 'X-User-Id': this.getUserId() } }
        );
    }

    deleteTag(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`, {
            headers: { 'X-User-Id': this.getUserId() },
        });
    }
}
