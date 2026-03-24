import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PlanService } from '../../../../services/plan.service';
import { TrainingService } from '../../../../services/training.service';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { DAY_LABELS, DayOfWeek, PLAN_STATUS_COLORS, PlanDay, PlanWeek, TrainingPlan } from '../../../../models/plan.model';
import { Training } from '../../../../models/training.model';
import { combineLatest, map } from 'rxjs';

@Component({
  selector: 'app-plan-detail-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, SportIconComponent],
  templateUrl: './plan-detail-page.component.html',
  styleUrl: './plan-detail-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanDetailPageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private planService = inject(PlanService);
  private trainingService = inject(TrainingService);

  plan$ = this.planService.selectedPlan$;
  progress$ = this.planService.progress$;
  trainings$ = this.trainingService.trainings$;

  // Combine plan and trainings to resolve training titles
  trainingMap$ = this.trainings$.pipe(
    map((trainings) => {
      const m = new Map<string, Training>();
      trainings.forEach((t) => m.set(t.id, t));
      return m;
    }),
  );

  readonly DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  readonly DAY_LABELS = DAY_LABELS;
  readonly STATUS_COLORS = PLAN_STATUS_COLORS;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.planService.loadPlan(id);
      this.planService.loadProgress(id);
    }
  }

  activatePlan(plan: TrainingPlan): void {
    this.planService.activatePlan(plan.id).subscribe();
  }

  pausePlan(plan: TrainingPlan): void {
    this.planService.pausePlan(plan.id).subscribe();
  }

  deletePlan(plan: TrainingPlan): void {
    this.planService.deletePlan(plan.id).subscribe({
      next: () => this.router.navigate(['/plans']),
    });
  }

  getTrainingForDay(week: PlanWeek, day: DayOfWeek): PlanDay | undefined {
    return week.days?.find((d) => d.dayOfWeek === day);
  }

  getTrainingTitle(trainingId: string | undefined, trainingMap: Map<string, Training>): string {
    if (!trainingId) return '';
    return trainingMap.get(trainingId)?.title ?? 'Unknown';
  }

  computeDate(startDate: string, weekNumber: number, day: DayOfWeek): string {
    if (!startDate) return '';
    const start = new Date(startDate);
    const dayOffset = this.DAYS.indexOf(day);
    const date = new Date(start);
    date.setDate(start.getDate() + (weekNumber - 1) * 7 + dayOffset);
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  isCurrentWeek(startDate: string, weekNumber: number): boolean {
    if (!startDate) return false;
    const start = new Date(startDate);
    const weekStart = new Date(start);
    weekStart.setDate(start.getDate() + (weekNumber - 1) * 7);
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekStart.getDate() + 7);
    const now = new Date();
    return now >= weekStart && now < weekEnd;
  }
}
