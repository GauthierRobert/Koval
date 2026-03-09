import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RaceGoal, RaceGoalService } from '../../services/race-goal.service';

@Component({
  selector: 'app-goals-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent implements OnInit {
  private raceGoalService = inject(RaceGoalService);

  goals$ = this.raceGoalService.goals$;

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
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(dateStr + 'T00:00:00');
    return Math.round((target.getTime() - today.getTime()) / 86400000);
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

  private emptyForm(): Partial<RaceGoal> {
    return { sport: 'CYCLING', priority: 'A' };
  }
}
