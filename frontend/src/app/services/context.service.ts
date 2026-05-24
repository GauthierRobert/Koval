import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { filter } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import {
  CoachAthleteContext,
  ContextEntry,
  ContextSections,
  MyContext,
} from '../models/context.model';

@Injectable({ providedIn: 'root' })
export class ContextService {
  private readonly apiUrl = `${environment.apiUrl}/api`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  private myContextSubject = new BehaviorSubject<MyContext | null>(null);
  myContext$ = this.myContextSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(true);
  loading$ = this.loadingSubject.asObservable();

  constructor() {
    this.authService.user$.pipe(filter((u) => !!u)).subscribe(() => this.loadMyContext());
  }

  loadMyContext(): void {
    this.loadingSubject.next(true);
    this.http.get<MyContext>(`${this.apiUrl}/context/me`).subscribe({
      next: (ctx) =>
        this.ngZone.run(() => {
          this.myContextSubject.next(ctx);
          this.loadingSubject.next(false);
        }),
      error: () => this.ngZone.run(() => this.loadingSubject.next(false)),
    });
  }

  saveMyContext(sections: ContextSections): Observable<MyContext> {
    return this.http
      .put<MyContext>(`${this.apiUrl}/context/me`, { sections })
      .pipe(tap((ctx) => this.ngZone.run(() => this.myContextSubject.next(ctx))));
  }

  getAthleteContext(athleteId: string): Observable<CoachAthleteContext> {
    return this.http.get<CoachAthleteContext>(`${this.apiUrl}/coach/athletes/${athleteId}/context`);
  }

  saveAthleteContext(athleteId: string, sections: ContextSections): Observable<ContextEntry> {
    return this.http.put<ContextEntry>(`${this.apiUrl}/coach/athletes/${athleteId}/context`, {
      sections,
    });
  }
}
