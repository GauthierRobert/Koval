import {ChangeDetectionStrategy, Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {map} from 'rxjs/operators';
import {Group, GroupService} from '../../../services/group.service';
import {CoachService, InviteCode} from '../../../services/coach.service';
import {User} from '../../../services/auth.service';
import {Router} from '@angular/router';

@Component({
  selector: 'app-group-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './group-management.component.html',
  styleUrl: './group-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupManagementComponent implements OnInit {
  private groupsSubject = new BehaviorSubject<Group[]>([]);
  private athletesSubject = new BehaviorSubject<User[]>([]);
  private inviteCodesSubject = new BehaviorSubject<InviteCode[]>([]);

  groupData$ = combineLatest([this.groupsSubject, this.athletesSubject, this.inviteCodesSubject]).pipe(
    map(([groups, athletes, codes]) => groups.map(group => {
      const matchingCode = codes.find(c => c.groupIds.includes(group.id) && c.active);
      return {
        ...group,
        groupAthletes: athletes.filter(a => a.groups?.includes(group.name)),
        inviteCode: matchingCode?.code,
      };
    }))
  );

  editingGrouId: string | null = null;
  editingName = '';
  newGroupName = '';
  newGroupMaxAthletes = 0;

  copiedCodeId: string | null = null;

  constructor(
    private groupService: GroupService,
    private coachService: CoachService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadGroups();
  }

  private loadGroups(): void {
    this.groupService.getGroups().subscribe({
      next: (groups) => {
        this.groupsSubject.next(groups);
        this.coachService.getAthletes().subscribe({
          next: (athletes) => this.athletesSubject.next(athletes),
          error: () => {},
        });
        this.coachService.getInviteCodes().subscribe({
          next: (codes) => this.inviteCodesSubject.next(codes),
          error: () => {},
        });
      },
      error: () => this.groupsSubject.next([]),
    });
  }

  startEdit(group: Group): void {
    this.editingGrouId = group.id;
    this.editingName = group.name;
  }

  saveEdit(group: Group): void {
    if (!this.editingName.trim() || this.editingName.trim() === group.name) {
      this.editingGrouId = null;
      return;
    }
    this.groupService.renameGroup(group.id, this.editingName.trim()).subscribe({
      next: () => {
        this.editingGrouId = null;
        this.loadGroups();
      },
      error: () => { this.editingGrouId = null; },
    });
  }

  cancelEdit(): void {
    this.editingGrouId = null;
  }

  removeAthleteFromTag(athlete: User, group: Group): void {
    this.coachService.removeAthleteGroup(athlete.id, group.name).subscribe({
      next: () => this.loadGroups(),
      error: () => {},
    });
  }

  createGroup(): void {
    if (!this.newGroupName.trim()) return;
    const name = this.newGroupName.trim();
    const maxAthletes = this.newGroupMaxAthletes;
    this.groupService.createGroup(name, maxAthletes).subscribe({
      next: (created) => {
        this.newGroupName = '';
        this.newGroupMaxAthletes = 0;
        // Auto-generate random invite code for the new group
        this.coachService.generateInviteCode([created.id], maxAthletes || 0).subscribe({
          next: () => this.loadGroups(),
          error: () => this.loadGroups(),
        });
      },
      error: () => {},
    });
  }

  deleteGroup(group: Group): void {
    if (!confirm(`Delete group "${group.name}"? This will remove it from all athletes.`)) return;
    this.groupService.deleteGroup(group.id).subscribe({
      next: () => this.loadGroups(),
      error: () => {},
    });
  }

  copyCode(tag: { id: string; inviteCode?: string }): void {
    if (tag.inviteCode) {
      navigator.clipboard.writeText(tag.inviteCode);
      this.copiedCodeId = tag.id;
      setTimeout(() => (this.copiedCodeId = null), 2000);
    }
  }

  navigateToCoach(athleteId: string): void {
    this.router.navigate(['/coach'], { queryParams: { athleteId } });
  }
}
