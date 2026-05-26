import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs';
import {User} from '../../../../services/auth.service';
import {SavedSession} from '../../../../services/history.service';
import {SessionData} from '../../../../models/session-types.model';
import {effectiveAlignmentScore} from '../../../../models/alignment.model';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {SessionAnalysisComponent} from '../../session-analysis/session-analysis.component';
import {AlignmentBadgeComponent} from '../../../shared/alignment-badge/alignment-badge.component';
import {formatTimeHMS} from '../../../shared/format/format.utils';

@Component({
  selector: 'app-coach-history-tab',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    SportIconComponent,
    SessionAnalysisComponent,
    AlignmentBadgeComponent,
  ],
  templateUrl: './coach-history-tab.component.html',
  styleUrl: './coach-history-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoachHistoryTabComponent {
  @Input() athleteSessions$!: Observable<SessionData[]>;
  @Input() athleteSessionsError$!: Observable<boolean>;
  @Input() selectedAthleteSession$!: Observable<SavedSession | null>;
  @Input() selectedAthlete!: User;

  @Output() openSessionAnalysis = new EventEmitter<SessionData>();
  @Output() closeSessionAnalysis = new EventEmitter<void>();

  formatSessionDur(sec: number): string {
    return formatTimeHMS(sec);
  }

  alignmentScore(s: SessionData): number | null {
    return effectiveAlignmentScore(s.alignmentScore);
  }

  trackSessionById(s: SessionData): string {
    return s.id;
  }
}
