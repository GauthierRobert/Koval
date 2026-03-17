import {inject, Injectable, NgZone} from '@angular/core';
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
  private ngZone = inject(NgZone);

  executeAction(
    message: string,
    actionType: AIActionType,
    context: ActionContext = {},
  ): Observable<ActionResult> {
    return new Observable<ActionResult>((observer) => {
      this.http
        .post<ActionResult>(this.apiUrl, { message, actionType, context })
        .subscribe({
          next: (result) => {
            this.ngZone.run(() => {
              observer.next(result);
              observer.complete();
            });
          },
          error: (err) => {
            this.ngZone.run(() => observer.error(err));
          },
        });
    });
  }
}
