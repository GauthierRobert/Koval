import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { map } from 'rxjs/operators';
import { RaceGoal, RaceGoalService } from '../../../services/race-goal.service';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import { daysUntil as sharedDaysUntil, weeksUntil as sharedWeeksUntil } from '../../shared/format/format.utils';

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SportIconComponent],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent implements OnInit {
  private raceGoalService = inject(RaceGoalService);

  goals$ = this.raceGoalService.goals$.pipe(
    map(goals => this.sortGoals(goals))
  );

  isFormOpen = false;
  editingGoal: RaceGoal | null = null;

  form: Partial<RaceGoal> = this.emptyForm();

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  readonly priorities: Array<{ value: 'A' | 'B' | 'C'; label: string }> = [
    { value: 'A', label: 'A — Goal Race' },
    { value: 'B', label: 'B — Target Race' },
    { value: 'C', label: 'C — Training Race' },
  ];

  ngOnInit(): void {
    this.raceGoalService.loadGoals();
  }

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  daysUntil(dateStr: string): number {
    return sharedDaysUntil(dateStr);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  openCreate(): void {
    this.editingGoal = null;
    this.form = this.emptyForm();
    this.isFormOpen = true;
  }

  openEdit(goal: RaceGoal): void {
    this.editingGoal = goal;
    this.form = { ...goal };
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  save(): void {
    if (!this.form.title || !this.form.raceDate || !this.form.sport || !this.form.priority) return;
    if (this.editingGoal) {
      this.raceGoalService.updateGoal(this.editingGoal.id, this.form).subscribe(() => {
        this.isFormOpen = false;
      });
    } else {
      this.raceGoalService.createGoal(this.form).subscribe(() => {
        this.isFormOpen = false;
      });
    }
  }

  delete(goal: RaceGoal): void {
    if (confirm(`Delete "${goal.title}"?`)) {
      this.raceGoalService.deleteGoal(goal.id);
    }
  }

  weeksUntil(dateStr: string): number {
    return sharedWeeksUntil(dateStr);
  }

  getProgressPercent(goal: RaceGoal): number {
    if (!goal.createdAt) return 0;
    const created = new Date(goal.createdAt).getTime();
    const race = new Date(goal.raceDate + 'T00:00:00').getTime();
    const now = Date.now();
    if (now >= race) return 100;
    if (now <= created) return 0;
    return Math.round(((now - created) / (race - created)) * 100);
  }

  isUpcoming(goal: RaceGoal): boolean {
    return this.daysUntil(goal.raceDate) >= 0;
  }

  private sortGoals(goals: RaceGoal[]): RaceGoal[] {
    const priorityOrder: Record<string, number> = { A: 0, B: 1, C: 2 };
    const upcoming = goals.filter(g => this.isUpcoming(g));
    const past = goals.filter(g => !this.isUpcoming(g));

    const sortFn = (a: RaceGoal, b: RaceGoal) => {
      const pa = priorityOrder[a.priority] ?? 3;
      const pb = priorityOrder[b.priority] ?? 3;
      if (pa !== pb) return pa - pb;
      return new Date(a.raceDate).getTime() - new Date(b.raceDate).getTime();
    };

    return [...upcoming.sort(sortFn), ...past.sort((a, b) =>
      new Date(b.raceDate).getTime() - new Date(a.raceDate).getTime()
    )];
  }

  private emptyForm(): Partial<RaceGoal> {
    return { sport: 'CYCLING', priority: 'A' };
  }
}
