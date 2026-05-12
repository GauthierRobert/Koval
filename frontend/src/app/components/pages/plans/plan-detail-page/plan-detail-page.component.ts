import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PlanService } from '../../../../services/plan.service';
import { TrainingService } from '../../../../services/training.service';
import { AuthService } from '../../../../services/auth.service';
import { CoachService } from '../../../../services/coach.service';
import { User } from '../../../../models/user.model';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { WorkoutPickerModalComponent } from '../workout-picker-modal/workout-picker-modal.component';
import { DAY_LABELS, DayOfWeek, PlanAnalytics, PlanDay, PlanWeek, PlanWeekAnalytics, TrainingPlan } from '../../../../models/plan.model';
import { Training, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType } from '../../../../models/training.model';
import { map } from 'rxjs';

@Component({
  selector: 'app-plan-detail-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, SportIconComponent, WorkoutPickerModalComponent],
  templateUrl: './plan-detail-page.component.html',
  styleUrl: './plan-detail-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanDetailPageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private planService = inject(PlanService);
  private trainingService = inject(TrainingService);
  private authService = inject(AuthService);
  private coachService = inject(CoachService);

  plan$ = this.planService.selectedPlan$;
  progress$ = this.planService.progress$;
  analytics$ = this.planService.analytics$;
  trainings$ = this.trainingService.trainings$;

  trainingMap$ = this.trainings$.pipe(
    map((trainings) => {
      const m = new Map<string, Training>();
      trainings.forEach((t) => m.set(t.id, t));
      return m;
    }),
  );

  readonly DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  readonly DAY_LABELS = DAY_LABELS;

  // Workout picker state
  pickerOpen = false;
  pickerWeekNumber = 1;
  pickerDayOfWeek: DayOfWeek = 'MONDAY';
  pickerCurrentTrainingId?: string;
  pickerCurrentNotes?: string;
  // Index of the training inside the day's trainingIds list being edited.
  // null = appending a new workout to the day.
  pickerEditIndex: number | null = null;

  // Inline editing state
  editingWeek: number | null = null;
  editWeekLabel = '';
  editWeekTss: number | null = null;
  editingMeta = false;
  editTitle = '';
  editDescription = '';

  // Confirmation dialog state
  confirmAction: 'delete' | 'activate' | 'pause' | 'complete' | null = null;

  // Activation dialog state
  activationStartDate = '';

  // Coach athlete assignment
  isCoach = false;
  athletes: User[] = [];
  selectedAthleteIds: string[] = [];


  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.planService.loadPlan(id);
      this.planService.loadProgress(id);
      this.planService.loadAnalytics(id);
    }
    this.trainingService.loadTrainings();
    this.isCoach = this.authService.isCoach();
    if (this.isCoach) {
      this.coachService.getAthletes().subscribe((athletes) => {
        this.athletes = athletes;
      });
    }
  }

  isEditable(plan: TrainingPlan): boolean {
    return plan.status === 'DRAFT' || plan.status === 'PAUSED' || plan.status === 'ACTIVE';
  }

  // ── Workout Picker ──────────────────────────────────────

  /**
   * Open the picker to add another workout to a day. Use this for the "+ add"
   * affordance on a day slot — triathletes often schedule 2+ sessions per day.
   */
  openWorkoutPickerForAdd(plan: TrainingPlan, week: PlanWeek, day: DayOfWeek): void {
    if (!this.isEditable(plan)) return;
    const existingDay = this.getPlanDay(week, day);
    this.pickerWeekNumber = week.weekNumber;
    this.pickerDayOfWeek = day;
    this.pickerCurrentTrainingId = undefined;
    this.pickerCurrentNotes = existingDay?.notes;
    this.pickerEditIndex = null;
    this.pickerOpen = true;
  }

  /** Open the picker to edit / replace one specific workout within a day. */
  openWorkoutPickerForEdit(
    plan: TrainingPlan,
    week: PlanWeek,
    day: DayOfWeek,
    trainingIndex: number,
  ): void {
    if (!this.isEditable(plan)) return;
    const planDay = this.getPlanDay(week, day);
    if (!planDay) return;
    this.pickerWeekNumber = week.weekNumber;
    this.pickerDayOfWeek = day;
    this.pickerCurrentTrainingId = planDay.trainingIds[trainingIndex];
    this.pickerCurrentNotes = planDay.notes;
    this.pickerEditIndex = trainingIndex;
    this.pickerOpen = true;
  }

  onPickerConfirmed(plan: TrainingPlan, result: { trainingId: string; notes: string } | null): void {
    const week = plan.weeks.find((w) => w.weekNumber === this.pickerWeekNumber);
    if (!week) return;

    let day = this.getPlanDay(week, this.pickerDayOfWeek);

    if (result === null) {
      if (!day) {
        this.pickerOpen = false;
        return;
      }
      if (this.pickerEditIndex !== null && this.pickerEditIndex < day.trainingIds.length) {
        day.trainingIds.splice(this.pickerEditIndex, 1);
      } else {
        day.trainingIds = [];
      }
      if (day.trainingIds.length === 0) {
        week.days = week.days.filter((d) => d.dayOfWeek !== this.pickerDayOfWeek);
      }
    } else {
      if (!day) {
        day = {
          dayOfWeek: this.pickerDayOfWeek,
          trainingIds: [],
          scheduledWorkoutIds: [],
        };
        week.days.push(day);
      }
      if (this.pickerEditIndex !== null && this.pickerEditIndex < day.trainingIds.length) {
        day.trainingIds[this.pickerEditIndex] = result.trainingId;
      } else if (!day.trainingIds.includes(result.trainingId)) {
        day.trainingIds.push(result.trainingId);
      }
      day.notes = result.notes || undefined;
    }

    this.planService.updatePlan(plan.id, plan).subscribe();
    this.pickerOpen = false;
  }

  // ── Week Label Editing ──────────────────────────────────

  startEditWeek(week: PlanWeek): void {
    this.editingWeek = week.weekNumber;
    this.editWeekLabel = week.label ?? '';
    this.editWeekTss = week.targetTss ?? null;
  }

  saveWeekEdit(plan: TrainingPlan): void {
    const week = plan.weeks.find((w) => w.weekNumber === this.editingWeek);
    if (!week) return;
    week.label = this.editWeekLabel.trim() || undefined;
    week.targetTss = this.editWeekTss ?? undefined;
    this.editingWeek = null;
    this.planService.updatePlan(plan.id, plan).subscribe();
  }

  cancelWeekEdit(): void {
    this.editingWeek = null;
  }

  // ── Metadata Editing ────────────────────────────────────

  startEditMeta(plan: TrainingPlan): void {
    this.editingMeta = true;
    this.editTitle = plan.title;
    this.editDescription = plan.description ?? '';
  }

  saveMetaEdit(plan: TrainingPlan): void {
    plan.title = this.editTitle.trim();
    plan.description = this.editDescription.trim() || undefined;
    this.editingMeta = false;
    this.planService.updatePlan(plan.id, plan).subscribe();
  }

  cancelMetaEdit(): void {
    this.editingMeta = false;
  }

  // ── Confirmation Dialogs ────────────────────────────────

  showConfirm(action: 'delete' | 'activate' | 'pause' | 'complete'): void {
    if (action === 'activate') {
      this.activationStartDate = this.getNextMonday();
      this.selectedAthleteIds = [];
    }
    this.confirmAction = action;
  }

  toggleAthlete(athleteId: string): void {
    const idx = this.selectedAthleteIds.indexOf(athleteId);
    if (idx >= 0) {
      this.selectedAthleteIds.splice(idx, 1);
    } else {
      this.selectedAthleteIds.push(athleteId);
    }
  }

  isAthleteSelected(athleteId: string): boolean {
    return this.selectedAthleteIds.includes(athleteId);
  }

  private getNextMonday(): string {
    const today = new Date();
    const dayOfWeek = today.getDay();
    const daysUntilMonday = dayOfWeek === 0 ? 1 : dayOfWeek === 1 ? 7 : 8 - dayOfWeek;
    const nextMonday = new Date(today);
    nextMonday.setDate(today.getDate() + daysUntilMonday);
    return nextMonday.toISOString().split('T')[0];
  }

  closeConfirm(): void {
    this.confirmAction = null;
  }

  executeConfirmedAction(plan: TrainingPlan): void {
    switch (this.confirmAction) {
      case 'delete':
        this.planService.deletePlan(plan.id).subscribe({
          next: () => this.router.navigate(['/plans']),
        });
        break;
      case 'activate':
        this.planService
          .activatePlan(
            plan.id,
            this.activationStartDate,
            this.selectedAthleteIds.length ? this.selectedAthleteIds : undefined
          )
          .subscribe({
            next: () => this.planService.loadProgress(plan.id),
          });
        break;
      case 'pause':
        this.planService.pausePlan(plan.id).subscribe({
          next: () => this.planService.loadProgress(plan.id),
        });
        break;
      case 'complete':
        this.planService.completePlan(plan.id).subscribe();
        break;
    }
    this.confirmAction = null;
  }

  // ── Helpers ─────────────────────────────────────────────

  getPlanDay(week: PlanWeek, day: DayOfWeek): PlanDay | undefined {
    return week.days?.find((d) => d.dayOfWeek === day);
  }

  dayHasWorkouts(week: PlanWeek, day: DayOfWeek): boolean {
    const planDay = this.getPlanDay(week, day);
    return !!planDay && planDay.trainingIds.length > 0;
  }

  getTrainingTitle(trainingId: string | undefined, trainingMap: Map<string, Training>): string {
    if (!trainingId) return '';
    return trainingMap.get(trainingId)?.title ?? 'Unknown';
  }

  getTraining(trainingId: string | undefined, trainingMap: Map<string, Training>): Training | undefined {
    return trainingId ? trainingMap.get(trainingId) : undefined;
  }

  getTypeColor(type: TrainingType | undefined): string {
    return type ? TRAINING_TYPE_COLORS[type] : '#888';
  }

  getTypeLabel(type: TrainingType | undefined): string {
    return type ? TRAINING_TYPE_LABELS[type] : '';
  }

  computeDate(startDate: string, weekNumber: number, day: DayOfWeek): string {
    if (!startDate) return '';
    const start = new Date(startDate);
    const dayOffset = this.DAYS.indexOf(day);
    const date = new Date(start);
    date.setDate(start.getDate() + (weekNumber - 1) * 7 + dayOffset);
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  adherenceColor(percent: number): string {
    if (percent > 80) return 'var(--success-color, #22c55e)';
    if (percent >= 50) return '#f59e0b';
    return 'var(--danger-color, #ef4444)';
  }

  tssBarWidth(week: PlanWeekAnalytics, maxTss: number): number {
    const value = week.targetTss ?? week.actualTss;
    return maxTss > 0 ? (value / maxTss) * 100 : 0;
  }

  tssFilledWidth(week: PlanWeekAnalytics): number {
    if (!week.targetTss || week.targetTss === 0) return week.actualTss > 0 ? 100 : 0;
    return Math.min((week.actualTss / week.targetTss) * 100, 100);
  }

  maxTss(analytics: PlanAnalytics): number {
    return Math.max(...analytics.weeklyBreakdown.map((w) => Math.max(w.targetTss ?? 0, w.actualTss)), 1);
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
