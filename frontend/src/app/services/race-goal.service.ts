import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { filter } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface RaceGoal {
  id: string;
  athleteId: string;
  title: string;
  sport: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'TRIATHLON' | 'OTHER';
  raceDate: string; // YYYY-MM-DD
  priority: 'A' | 'B' | 'C';
  distance?: string;
  location?: string;
  notes?: string;
  targetTime?: string;
  createdAt?: string;
  raceId?: string; // optional reference to Race catalog entry
}

export const PRIORITY_COLORS: Record<string, string> = {
  A: '#F59E0B',
  B: '#60A5FA',
  C: '#9CA3AF',
};

@Injectable({ providedIn: 'root' })
export class RaceGoalService {
  private readonly apiUrl = `${environment.apiUrl}/api/goals`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  private goalsSubject = new BehaviorSubject<RaceGoal[]>([]);
  goals$ = this.goalsSubject.asObservable();

  nextGoal$: Observable<RaceGoal | null> = this.goals$.pipe(
    map((goals) => {
      const today = new Date().toISOString().split('T')[0];
      const future = goals.filter((g) => g.raceDate >= today).sort((a, b) => a.raceDate.localeCompare(b.raceDate));
      return future[0] ?? null;
    }),
  );

  constructor() {
    this.authService.user$.pipe(filter((u) => !!u)).subscribe(() => this.loadGoals());
  }

  loadGoals(): void {
    this.http.get<RaceGoal[]>(this.apiUrl).subscribe({
      next: (goals) => this.ngZone.run(() => this.goalsSubject.next(goals)),
      error: () => {},
    });
  }

  createGoal(goal: Partial<RaceGoal>): Observable<RaceGoal> {
    return new Observable((observer) => {
      this.http.post<RaceGoal>(this.apiUrl, goal).subscribe({
        next: (created) => {
          this.ngZone.run(() => {
            this.goalsSubject.next([...this.goalsSubject.value, created].sort((a, b) => a.raceDate.localeCompare(b.raceDate)));
            observer.next(created);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  updateGoal(id: string, goal: Partial<RaceGoal>): Observable<RaceGoal> {
    return new Observable((observer) => {
      this.http.put<RaceGoal>(`${this.apiUrl}/${id}`, goal).subscribe({
        next: (updated) => {
          this.ngZone.run(() => {
            const list = this.goalsSubject.value.map((g) => (g.id === id ? updated : g)).sort((a, b) => a.raceDate.localeCompare(b.raceDate));
            this.goalsSubject.next(list);
            observer.next(updated);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  deleteGoal(id: string): void {
    this.http.delete(`${this.apiUrl}/${id}`).subscribe({
      next: () => this.ngZone.run(() => this.goalsSubject.next(this.goalsSubject.value.filter((g) => g.id !== id))),
      error: () => {},
    });
  }

  linkToRace(goalId: string, raceId: string): Observable<RaceGoal> {
    return new Observable((observer) => {
      this.http.post<RaceGoal>(`${this.apiUrl}/${goalId}/link-race/${raceId}`, {}).subscribe({
        next: (updated) => {
          this.ngZone.run(() => {
            const list = this.goalsSubject.value.map((g) => (g.id === goalId ? updated : g));
            this.goalsSubject.next(list);
            observer.next(updated);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  getAthleteGoals(athleteId: string): Observable<RaceGoal[]> {
    return this.http.get<RaceGoal[]>(`${environment.apiUrl}/api/coach/athletes/${athleteId}/goals`);
  }

  getPriorityColor(priority: string): string {
    return PRIORITY_COLORS[priority] ?? '#9CA3AF';
  }
}
