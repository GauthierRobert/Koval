import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, catchError, of } from 'rxjs';
import { AlignmentService } from '../../../../services/alignment.service';
import { AlignmentHistoryPoint } from '../../../../models/alignment.model';
import { AlignmentEvolutionChartComponent } from '../../../shared/alignment-evolution-chart/alignment-evolution-chart.component';
import { AlignmentBadgeComponent } from '../../../shared/alignment-badge/alignment-badge.component';

/**
 * Coach-dashboard tab: a managed athlete's plan-alignment over time. Shows the evolution chart plus
 * a per-session list with the athlete's and coach/AI ratings side by side. Loads its own data for
 * the selected athlete over the trailing window.
 */
@Component({
  selector: 'app-coach-alignment-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, AlignmentEvolutionChartComponent, AlignmentBadgeComponent],
  templateUrl: './coach-alignment-tab.component.html',
  styleUrl: './coach-alignment-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachAlignmentTabComponent {
  private readonly windowDays = 120;
  private alignmentService = inject(AlignmentService);

  private pointsSubject = new BehaviorSubject<AlignmentHistoryPoint[] | null>(null);
  points$ = this.pointsSubject.asObservable();

  @Input({ required: true }) set athleteId(id: string) {
    if (!id) return;
    this.pointsSubject.next(null);
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - this.windowDays);
    this.alignmentService
      .getHistory(this.toKey(from), this.toKey(to), id)
      .pipe(catchError(() => of([] as AlignmentHistoryPoint[])))
      .subscribe((pts) => this.pointsSubject.next(pts));
  }

  /** Most recent first for the list under the chart. */
  reversed(points: AlignmentHistoryPoint[]): AlignmentHistoryPoint[] {
    return [...points].reverse();
  }

  private toKey(d: Date): string {
    return d.toISOString().split('T')[0];
  }
}
