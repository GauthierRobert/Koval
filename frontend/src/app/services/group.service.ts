import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {environment} from '../../environments/environment';

export interface Group {
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
export class GroupService {
    private apiUrl = `${environment.apiUrl}/api/groups`;
    private http = inject(HttpClient);

    getGroups(): Observable<Group[]> {
        return this.http.get<Group[]>(this.apiUrl);
    }

    createGroup(name: string, maxAthletes: number = 0): Observable<Group> {
        return this.http.post<Group>(this.apiUrl, { name, maxAthletes });
    }

    renameGroup(id: string, name: string): Observable<Group> {
        return this.http.put<Group>(`${this.apiUrl}/${id}`, { name }).pipe(catchError(() => of(null as any)));
    }

    deleteGroup(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }

    leaveGroup(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}/leave`);
    }
}
