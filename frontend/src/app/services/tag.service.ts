import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {of} from 'rxjs';
import {environment} from '../../environments/environment';

export interface Tag {
    id: string;
    name: string;
    coachId: string;
    athleteIds: string[];
    maxAthletes: number;
    createdAt: string;
}

@Injectable({
    providedIn: 'root',
})
export class TagService {
    private apiUrl = `${environment.apiUrl}/api/tags`;
    private http = inject(HttpClient);

    getTags(): Observable<Tag[]> {
        return this.http.get<Tag[]>(this.apiUrl);
    }

    createTag(name: string, maxAthletes: number = 0): Observable<Tag> {
        return this.http.post<Tag>(this.apiUrl, { name, maxAthletes });
    }

    renameTag(id: string, name: string): Observable<Tag> {
        return this.http.put<Tag>(`${this.apiUrl}/${id}`, { name }).pipe(catchError(() => of(null as any)));
    }

    deleteTag(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}
