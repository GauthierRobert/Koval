import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, catchError, of } from 'rxjs';
import {
  FamilyEffectiveness,
  TrainingEffectivenessReport,
  TrainingEffectivenessService,
} from '../../../../services/training-effectiveness.service';

/**
 * Coach-dashboard tab: which workout families produced the most fitness return for this athlete
 * over the trailing window. Wraps {@link TrainingEffectivenessService} and renders the ranking
 * table, period gain, and the underlying first- vs second-half power-curve split.
 */
@Component({
  selector: 'app-coach-effectiveness-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './coach-effectiveness-tab.component.html',
  styleUrl: './coach-effectiveness-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachEffectivenessTabComponent {
  private readonly defaultWindowDays = 90;
  readonly windowOptions = [60, 90, 180];
  readonly reportedDurations = [300, 1200, 3600];
  readonly durationLabels: Record<number, string> = {
    300: '5min',
    1200: '20min',
    3600: '1h',
  };

  private service = inject(TrainingEffectivenessService);
  private currentAthleteId: string | null = null;

  windowDays = this.defaultWindowDays;

  private reportSubject = new BehaviorSubject<TrainingEffectivenessReport | null | 'error'>(null);
  report$ = this.reportSubject.asObservable();

  @Input({ required: true }) set athleteId(id: string) {
    if (!id) return;
    this.currentAthleteId = id;
    this.load();
  }

  setWindow(days: number): void {
    if (this.windowDays === days) return;
    this.windowDays = days;
    this.load();
  }

  asReport(
    value: TrainingEffectivenessReport | 'error' | null,
  ): TrainingEffectivenessReport | null {
    return value === 'error' || value === null ? null : value;
  }

  isError(value: TrainingEffectivenessReport | 'error' | null): boolean {
    return value === 'error';
  }

  trackFamily(_index: number, item: FamilyEffectiveness): string {
    return item.family;
  }

  trackDuration(_index: number, item: number): number {
    return item;
  }

  effectivenessClass(value: number | null): string {
    if (value === null) return 'eff-na';
    if (value > 1.5) return 'eff-strong';
    if (value > 0) return 'eff-positive';
    if (value < -0.5) return 'eff-negative';
    return 'eff-neutral';
  }

  private load(): void {
    if (!this.currentAthleteId) return;
    this.reportSubject.next(null);
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - this.windowDays);
    this.service
      .evaluate(this.currentAthleteId, this.toKey(from), this.toKey(to))
      .pipe(catchError(() => of<'error'>('error')))
      .subscribe((r) => this.reportSubject.next(r));
  }

  private toKey(d: Date): string {
    return d.toISOString().split('T')[0];
  }
}
