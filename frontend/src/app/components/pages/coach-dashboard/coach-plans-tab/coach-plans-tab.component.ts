import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Input,
  NgZone,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AthletePlanSummary, CoachService } from '../../../../services/coach.service';
import { PLAN_STATUS_COLORS, PlanStatus } from '../../../../models/plan.model';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';

@Component({
  selector: 'app-coach-plans-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './coach-plans-tab.component.html',
  styleUrl: './coach-plans-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachPlansTabComponent implements OnChanges {
  @Input() athleteId!: string;

  private readonly coachService = inject(CoachService);
  private readonly router = inject(Router);
  private readonly ngZone = inject(NgZone);

  private plansSubject = new BehaviorSubject<AthletePlanSummary[]>([]);
  plans$ = this.plansSubject.asObservable();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['athleteId'] && this.athleteId) {
      this.loadPlans();
    }
  }

  private loadPlans(): void {
    this.coachService.getAthletePlans(this.athleteId).subscribe({
      next: (plans) => this.ngZone.run(() => this.plansSubject.next(plans)),
      error: () => this.ngZone.run(() => this.plansSubject.next([])),
    });
  }

  statusColor(status: string): string {
    return PLAN_STATUS_COLORS[status as PlanStatus] ?? '#6b7280';
  }

  adherencePercent(plan: AthletePlanSummary): number | null {
    if (!plan.analytics || plan.analytics.totalTargetTss <= 0) return null;
    return plan.analytics.overallAdherencePercent;
  }

  openPlan(plan: AthletePlanSummary): void {
    this.router.navigate(['/plans', plan.planId]);
  }

  trackByPlanId(plan: AthletePlanSummary): string {
    return plan.planId;
  }
}
