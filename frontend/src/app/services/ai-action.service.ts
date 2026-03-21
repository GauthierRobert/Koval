import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';

export type AIActionType = 'ZONE_CREATION' | 'TRAINING_WITH_SESSION' | 'TRAINING_FROM_NOTATION';

export interface ActionContext {
  clubId?: string;
  clubGroupId?: string;
  coachGroupId?: string;
  sessionId?: string;
  sport?: string;
  zoneSystemId?: string;
}

export interface ActionResult {
  content: string;
  success: boolean;
}

@Injectable({ providedIn: 'root' })
export class AIActionService {
  private readonly apiUrl = `${environment.apiUrl}/api/ai/action`;
  private http = inject(HttpClient);

  executeAction(
    message: string,
    actionType: AIActionType,
    context: ActionContext = {},
  ): Observable<ActionResult> {
    return this.http.post<ActionResult>(this.apiUrl, { message, actionType, context });
  }
}
