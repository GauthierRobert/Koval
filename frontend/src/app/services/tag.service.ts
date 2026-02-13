import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Tag {
    id: string;
    name: string;
    coachId: string;
    athleteIds: string[];
    createdAt: string;
}

@Injectable({
    providedIn: 'root',
})
export class TagService {
    private apiUrl = 'http://localhost:8080/api/tags';
    private http = inject(HttpClient);

    getTags(): Observable<Tag[]> {
        return this.http.get<Tag[]>(this.apiUrl);
    }

    createTag(name: string): Observable<Tag> {
        return this.http.post<Tag>(this.apiUrl, { name });
    }

    deleteTag(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}
