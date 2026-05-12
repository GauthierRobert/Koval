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
  @Input() multiSelectMode = false;
  @Input() multiSelectedIds: ReadonlySet<string> = new Set();
  @Input() athleteMetrics$!: Observable<{
    ctl: number;
    atl: number;
    tsb: number;
    ctlTrend: number;
    atlTrend: number;
  } | null>;
  @Input() totalAthleteCount = 0;
  @Input() loading = false;

  readonly skeletonRows = Array.from({ length: 10 }, (_, i) => i);

  @Output() selectAthlete = new EventEmitter<User>();
  @Output() toggleTagFilter = new EventEmitter<string>();
  @Output() toggleClubFilter = new EventEmitter<string>();
  @Output() addAthlete = new EventEmitter<void>();
  @Output() clearFilters = new EventEmitter<void>();
  @Output() toggleMultiSelect = new EventEmitter<void>();
  @Output() toggleAthleteSelection = new EventEmitter<User>();
  @Output() bulkAssign = new EventEmitter<void>();

  isSelected(athleteId: string): boolean {
    return this.multiSelectedIds.has(athleteId);
  }

  onAthleteRowClick(athlete: User): void {
    if (this.multiSelectMode) {
      this.toggleAthleteSelection.emit(athlete);
    } else {
      this.selectAthlete.emit(athlete);
    }
  }

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
