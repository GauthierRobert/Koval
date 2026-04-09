import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import { ActivePlanSummary, PlanAnalytics, PlanProgress, TrainingPlan } from '../models/plan.model';

@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly apiUrl = `${environment.apiUrl}/api/plans`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  private plansSubject = new BehaviorSubject<TrainingPlan[]>([]);
  plans$ = this.plansSubject.asObservable();

  private selectedPlanSubject = new BehaviorSubject<TrainingPlan | null>(null);
  selectedPlan$ = this.selectedPlanSubject.asObservable();

  private progressSubject = new BehaviorSubject<PlanProgress | null>(null);
  progress$ = this.progressSubject.asObservable();

  private analyticsSubject = new BehaviorSubject<PlanAnalytics | null>(null);
  analytics$ = this.analyticsSubject.asObservable();

  private activePlanSubject = new BehaviorSubject<ActivePlanSummary | null>(null);
  activePlan$ = this.activePlanSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  constructor() {
    this.authService.user$.pipe(filter((u) => !!u)).subscribe(() => {
      this.loadPlans();
      this.loadActivePlan();
    });
  }

  loadPlans(): void {
    this.loadingSubject.next(true);
    this.http.get<TrainingPlan[]>(this.apiUrl).subscribe({
      next: (plans) =>
        this.ngZone.run(() => {
          this.plansSubject.next(plans);
          this.loadingSubject.next(false);
        }),
      error: () => this.ngZone.run(() => this.loadingSubject.next(false)),
    });
  }

  loadPlan(id: string): void {
    this.http.get<TrainingPlan>(`${this.apiUrl}/${id}`).subscribe({
      next: (plan) => this.ngZone.run(() => this.selectedPlanSubject.next(plan)),
      error: (err) => console.error('Failed to load plan', err),
    });
  }

  loadProgress(planId: string): void {
    this.http.get<PlanProgress>(`${this.apiUrl}/${planId}/progress`).subscribe({
      next: (p) => this.ngZone.run(() => this.progressSubject.next(p)),
      error: (err) => console.error('Failed to load plan progress', err),
    });
  }

  loadAnalytics(planId: string): void {
    this.http.get<PlanAnalytics>(`${this.apiUrl}/${planId}/analytics`).subscribe({
      next: (a) => this.ngZone.run(() => this.analyticsSubject.next(a)),
      error: (err) => console.error('Failed to load plan analytics', err),
    });
  }

  loadActivePlan(): void {
    this.http.get<ActivePlanSummary>(`${this.apiUrl}/active`, { observe: 'response' }).subscribe({
      next: (res) =>
        this.ngZone.run(() => {
          this.activePlanSubject.next(res.status === 204 ? null : res.body);
        }),
      error: () => this.ngZone.run(() => this.activePlanSubject.next(null)),
    });
  }

  createPlan(plan: Partial<TrainingPlan>): Observable<TrainingPlan> {
    return this.http.post<TrainingPlan>(this.apiUrl, plan).pipe(
      tap((created) => {
        this.ngZone.run(() => {
          this.plansSubject.next([created, ...this.plansSubject.value]);
        });
      }),
    );
  }

  updatePlan(id: string, updates: Partial<TrainingPlan>): Observable<TrainingPlan> {
    return this.http.put<TrainingPlan>(`${this.apiUrl}/${id}`, updates).pipe(
      tap((updated) => {
        this.ngZone.run(() => {
          const plans = this.plansSubject.value.map((p) => (p.id === id ? updated : p));
          this.plansSubject.next(plans);
          this.selectedPlanSubject.next(updated);
        });
      }),
    );
  }

  activatePlan(id: string, startDate: string, athleteIds?: string[]): Observable<TrainingPlan> {
    const body: { startDate: string; athleteIds?: string[] } = { startDate };
    if (athleteIds && athleteIds.length) body.athleteIds = athleteIds;
    return this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/activate`, body).pipe(
      tap((activated) => {
        this.ngZone.run(() => {
          const plans = this.plansSubject.value.map((p) => (p.id === id ? activated : p));
          this.plansSubject.next(plans);
          this.selectedPlanSubject.next(activated);
        });
      }),
    );
  }

  pausePlan(id: string): Observable<TrainingPlan> {
    return this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/pause`, {}).pipe(
      tap((paused) => {
        this.ngZone.run(() => {
          const plans = this.plansSubject.value.map((p) => (p.id === id ? paused : p));
          this.plansSubject.next(plans);
          this.selectedPlanSubject.next(paused);
        });
      }),
    );
  }

  completePlan(id: string): Observable<TrainingPlan> {
    return this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/complete`, {}).pipe(
      tap((completed) => {
        this.ngZone.run(() => {
          const plans = this.plansSubject.value.map((p) => (p.id === id ? completed : p));
          this.plansSubject.next(plans);
          this.selectedPlanSubject.next(completed);
        });
      }),
    );
  }

  deletePlan(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        this.ngZone.run(() => {
          this.plansSubject.next(this.plansSubject.value.filter((p) => p.id !== id));
          if (this.selectedPlanSubject.value?.id === id) {
            this.selectedPlanSubject.next(null);
          }
        });
      }),
    );
  }
}
