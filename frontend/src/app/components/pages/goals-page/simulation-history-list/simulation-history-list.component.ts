import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {RaceGoal} from '../../../../services/race-goal.service';
import {SimulationRequest} from '../../../../services/race.service';

@Component({
  selector: 'app-simulation-history-list',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './simulation-history-list.component.html',
  styleUrl: './simulation-history-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SimulationHistoryListComponent {
  @Input() simRequests: SimulationRequest[] = [];
  @Input() goal!: RaceGoal;

  @Output() simulate = new EventEmitter<void>();
  @Output() rerun = new EventEmitter<SimulationRequest>();
  @Output() deleteReq = new EventEmitter<SimulationRequest>();

  formatSimLabel(req: SimulationRequest): string {
    return req.label ?? `${req.discipline} simulation`;
  }

  formatSimDate(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
}
