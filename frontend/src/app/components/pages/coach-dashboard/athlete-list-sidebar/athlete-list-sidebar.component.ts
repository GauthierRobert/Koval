import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {User} from '../../../../services/auth.service';
import {Group} from '../../../../services/group.service';
import {MyClubRoleEntry} from '../../../../services/club.service';
import {Observable} from 'rxjs';

@Component({
  selector: 'app-athlete-list-sidebar',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './athlete-list-sidebar.component.html',
  styleUrl: './athlete-list-sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthleteListSidebarComponent {
  @Input() athletes: User[] = [];
  @Input() allTags: Group[] = [];
  @Input() clubRoles: MyClubRoleEntry[] = [];
  @Input() activeTagFilter: string | null = null;
  @Input() activeClubFilter: string | null = null;
  @Input() selectedAthlete: User | null = null;
  @Input() athleteMetrics$!: Observable<{
    ctl: number;
    atl: number;
    tsb: number;
    ctlTrend: number;
    atlTrend: number;
  } | null>;
  @Input() totalAthleteCount = 0;

  @Output() selectAthlete = new EventEmitter<User>();
  @Output() toggleTagFilter = new EventEmitter<string>();
  @Output() toggleClubFilter = new EventEmitter<string>();
  @Output() addAthlete = new EventEmitter<void>();
  @Output() clearFilters = new EventEmitter<void>();

  getTagCount(tag: string): number {
    return this.athletes.length
      ? this.athletes.filter(a => a.groups?.includes(tag)).length
      : 0;
  }

  getClubCount(clubName: string): number {
    return this.athletes.length
      ? this.athletes.filter(a => a.clubs?.includes(clubName)).length
      : 0;
  }

  trackTagByName(group: Group): string {
    return group.name;
  }

  trackAthleteById(athlete: User): string {
    return athlete.id;
  }

  trackByValue(value: string): string {
    return value;
  }
}
