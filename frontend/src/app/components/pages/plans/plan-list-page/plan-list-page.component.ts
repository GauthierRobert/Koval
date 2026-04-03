import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PlanService } from '../../../../services/plan.service';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { PlanSportType, PlanStatus, TrainingPlan } from '../../../../models/plan.model';

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
  loading$ = this.planService.loading$;

  // Create form
  showCreateForm = false;
  newTitle = '';
  newDescription = '';
  newSport: PlanSportType = 'CYCLING';
  newDurationWeeks = 4;

  // Delete confirmation
  planToDelete: TrainingPlan | null = null;

  statusClass(status: PlanStatus): string {
    return 'status-' + status.toLowerCase();
  }

  workoutCount(plan: TrainingPlan): number {
    return plan.weeks?.reduce((sum, w) => sum + (w.days?.length ?? 0), 0) ?? 0;
  }

  openPlan(plan: TrainingPlan): void {
    this.router.navigate(['/plans', plan.id]);
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
  }

  createPlan(): void {
    if (!this.newTitle.trim()) return;

    this.planService
      .createPlan({
        title: this.newTitle.trim(),
        description: this.newDescription.trim(),
        sportType: this.newSport,
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

  confirmDelete(event: Event, plan: TrainingPlan): void {
    event.stopPropagation();
    this.planToDelete = plan;
  }

  cancelDelete(): void {
    this.planToDelete = null;
  }

  deletePlan(): void {
    if (!this.planToDelete) return;
    this.planService.deletePlan(this.planToDelete.id).subscribe();
    this.planToDelete = null;
  }

}
