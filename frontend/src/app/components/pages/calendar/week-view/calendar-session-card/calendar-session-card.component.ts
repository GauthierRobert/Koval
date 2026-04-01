import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { RouterModule } from '@angular/router';

import { SavedSession } from '../../../../../services/history.service';
import { SportIconComponent } from '../../../../shared/sport-icon/sport-icon.component';
import { CalendarEntry } from '../../calendar.component';
import { formatTimeHMS } from '../../../../shared/format/format.utils';

@Component({
  selector: 'app-calendar-session-card',
  standalone: true,
  imports: [CommonModule, RouterModule, SportIconComponent, TranslateModule],
  templateUrl: './calendar-session-card.component.html',
  styleUrl: './calendar-session-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarSessionCardComponent {
  @Input() entry!: CalendarEntry;

  @Output() openAnalysis = new EventEmitter<string>();
  @Output() linkToWorkout = new EventEmitter<SavedSession>();
  @Output() linkToClubSession = new EventEmitter<SavedSession>();

  get session(): SavedSession {
    return (this.entry as any).session;
  }

  formatSessionDuration(session: SavedSession): string {
    return formatTimeHMS(session.totalDuration);
  }
}
