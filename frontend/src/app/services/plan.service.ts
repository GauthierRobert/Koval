import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter } from 'rxjs/operators';
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
      error: () => {},
    });
  }

  loadProgress(planId: string): void {
    this.http.get<PlanProgress>(`${this.apiUrl}/${planId}/progress`).subscribe({
      next: (p) => this.ngZone.run(() => this.progressSubject.next(p)),
      error: () => {},
    });
  }

  loadAnalytics(planId: string): void {
    this.http.get<PlanAnalytics>(`${this.apiUrl}/${planId}/analytics`).subscribe({
      next: (a) => this.ngZone.run(() => this.analyticsSubject.next(a)),
      error: () => {},
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
    return new Observable((observer) => {
      this.http.post<TrainingPlan>(this.apiUrl, plan).subscribe({
        next: (created) => {
          this.ngZone.run(() => {
            this.plansSubject.next([created, ...this.plansSubject.value]);
          });
          observer.next(created);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  updatePlan(id: string, updates: Partial<TrainingPlan>): Observable<TrainingPlan> {
    return new Observable((observer) => {
      this.http.put<TrainingPlan>(`${this.apiUrl}/${id}`, updates).subscribe({
        next: (updated) => {
          this.ngZone.run(() => {
            const plans = this.plansSubject.value.map((p) => (p.id === id ? updated : p));
            this.plansSubject.next(plans);
            this.selectedPlanSubject.next(updated);
          });
          observer.next(updated);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  activatePlan(id: string, startDate: string, athleteIds?: string[]): Observable<TrainingPlan> {
    return new Observable((observer) => {
      const body: { startDate: string; athleteIds?: string[] } = { startDate };
      if (athleteIds && athleteIds.length) body.athleteIds = athleteIds;
      this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/activate`, body).subscribe({
        next: (activated) => {
          this.ngZone.run(() => {
            const plans = this.plansSubject.value.map((p) => (p.id === id ? activated : p));
            this.plansSubject.next(plans);
            this.selectedPlanSubject.next(activated);
          });
          observer.next(activated);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  pausePlan(id: string): Observable<TrainingPlan> {
    return new Observable((observer) => {
      this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/pause`, {}).subscribe({
        next: (paused) => {
          this.ngZone.run(() => {
            const plans = this.plansSubject.value.map((p) => (p.id === id ? paused : p));
            this.plansSubject.next(plans);
            this.selectedPlanSubject.next(paused);
          });
          observer.next(paused);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  completePlan(id: string): Observable<TrainingPlan> {
    return new Observable((observer) => {
      this.http.post<TrainingPlan>(`${this.apiUrl}/${id}/complete`, {}).subscribe({
        next: (completed) => {
          this.ngZone.run(() => {
            const plans = this.plansSubject.value.map((p) => (p.id === id ? completed : p));
            this.plansSubject.next(plans);
            this.selectedPlanSubject.next(completed);
          });
          observer.next(completed);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  deletePlan(id: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${id}`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.plansSubject.next(this.plansSubject.value.filter((p) => p.id !== id));
            if (this.selectedPlanSubject.value?.id === id) {
              this.selectedPlanSubject.next(null);
            }
          });
          observer.next();
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }
}
