import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';
import {AiAnalysis} from '../models/ai-analysis.model';

@Injectable({providedIn: 'root'})
export class AiAnalysisService {
    private readonly http = inject(HttpClient);
    private readonly base = `${environment.apiUrl}/api`;

    listForSession$(sessionId: string): Observable<AiAnalysis[]> {
        return this.http.get<AiAnalysis[]>(`${this.base}/sessions/${sessionId}/analyses`);
    }

    delete$(id: string): Observable<void> {
        return this.http.delete<void>(`${this.base}/analyses/${id}`);
    }
}
