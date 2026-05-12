import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';
import {CoachNote} from '../models/coach-note.model';

export interface CoachNoteListOptions {
    sessionId?: string;
    limit?: number;
}

@Injectable({providedIn: 'root'})
export class CoachNoteService {
    private readonly http = inject(HttpClient);
    private readonly base = `${environment.apiUrl}/api/coach`;

    listForAthlete$(athleteId: string, opts: CoachNoteListOptions = {}): Observable<CoachNote[]> {
        let params = new HttpParams();
        if (opts.sessionId) params = params.set('sessionId', opts.sessionId);
        if (opts.limit != null) params = params.set('limit', opts.limit);
        return this.http.get<CoachNote[]>(`${this.base}/athletes/${athleteId}/notes`, {params});
    }

    delete$(id: string): Observable<void> {
        return this.http.delete<void>(`${this.base}/notes/${id}`);
    }
}
