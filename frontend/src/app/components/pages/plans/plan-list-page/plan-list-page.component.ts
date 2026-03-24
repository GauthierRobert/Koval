import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PlanService } from '../../../../services/plan.service';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { DAY_LABELS, PLAN_STATUS_COLORS, PlanSportType, PlanStatus, TrainingPlan } from '../../../../models/plan.model';

@Component({
  selector: 'app-plan-list-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent],
  templateUrl: './plan-list-page.component.html',
  styleUrl: './plan-list-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanListPageComponent {
  private planService = inject(PlanService);
  private router = inject(Router);

  plans$ = this.planService.plans$;

  // Create form
  showCreateForm = false;
  newTitle = '';
  newDescription = '';
  newSport: PlanSportType = 'CYCLING';
  newStartDate = '';
  newDurationWeeks = 4;

  statusColor(status: PlanStatus): string {
    return PLAN_STATUS_COLORS[status] ?? '#6b7280';
  }

  workoutCount(plan: TrainingPlan): number {
    return plan.weeks?.reduce((sum, w) => sum + (w.days?.length ?? 0), 0) ?? 0;
  }

  openPlan(plan: TrainingPlan): void {
    this.router.navigate(['/plans', plan.id]);
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    if (this.showCreateForm && !this.newStartDate) {
      // Default to next Monday
      const today = new Date();
      const daysUntilMonday = ((8 - today.getDay()) % 7) || 7;
      const nextMonday = new Date(today);
      nextMonday.setDate(today.getDate() + daysUntilMonday);
      this.newStartDate = nextMonday.toISOString().split('T')[0];
    }
  }

  createPlan(): void {
    if (!this.newTitle.trim()) return;

    this.planService
      .createPlan({
        title: this.newTitle.trim(),
        description: this.newDescription.trim(),
        sportType: this.newSport,
        startDate: this.newStartDate,
        durationWeeks: this.newDurationWeeks,
        weeks: Array.from({ length: this.newDurationWeeks }, (_, i) => ({
          weekNumber: i + 1,
          days: [],
        })),
        athleteIds: [],
      })
      .subscribe({
        next: (plan) => {
          this.showCreateForm = false;
          this.newTitle = '';
          this.newDescription = '';
          this.router.navigate(['/plans', plan.id]);
        },
      });
  }

  deletePlan(event: Event, plan: TrainingPlan): void {
    event.stopPropagation();
    this.planService.deletePlan(plan.id).subscribe();
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
