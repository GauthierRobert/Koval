import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { GroupService, Group } from '../../../services/group.service';
import { CoachService } from '../../../services/coach.service';
import { User } from '../../../services/auth.service';
import { Router } from '@angular/router';
import { InviteCodeModalComponent } from '../../shared/invite-code-modal/invite-code-modal.component';

@Component({
  selector: 'app-tag-management',
  standalone: true,
  imports: [CommonModule, FormsModule, InviteCodeModalComponent],
  templateUrl: './tag-management.component.html',
  styleUrl: './tag-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TagManagementComponent implements OnInit {
  private groupsSubject = new BehaviorSubject<Group[]>([]);
  private athletesSubject = new BehaviorSubject<User[]>([]);

  tagData$ = combineLatest([this.groupsSubject, this.athletesSubject]).pipe(
    map(([groups, athletes]) => groups.map(group => ({
      ...group,
      tagAthletes: athletes.filter(a => a.groups?.includes(group.name))
    })))
  );

  editingTagId: string | null = null;
  editingName = '';
  newTagName = '';
  newTagMaxAthletes = 0;

  // Invite modal per group
  isInviteModalOpen = false;
  selectedTagForInvite: Group | null = null;

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
      },
      error: () => this.groupsSubject.next([]),
    });
  }

  startEdit(group: Group): void {
    this.editingTagId = group.id;
    this.editingName = group.name;
  }

  saveEdit(group: Group): void {
    if (!this.editingName.trim() || this.editingName.trim() === group.name) {
      this.editingTagId = null;
      return;
    }
    this.groupService.renameGroup(group.id, this.editingName.trim()).subscribe({
      next: () => {
        this.editingTagId = null;
        this.loadGroups();
      },
      error: () => { this.editingTagId = null; },
    });
  }

  cancelEdit(): void {
    this.editingTagId = null;
  }

  removeAthleteFromTag(athlete: User, group: Group): void {
    this.coachService.removeAthleteGroup(athlete.id, group.name).subscribe({
      next: () => this.loadGroups(),
      error: () => {},
    });
  }

  createTag(): void {
    if (!this.newTagName.trim()) return;
    this.groupService.createGroup(this.newTagName.trim(), this.newTagMaxAthletes).subscribe({
      next: () => {
        this.newTagName = '';
        this.newTagMaxAthletes = 0;
        this.loadGroups();
      },
      error: () => {},
    });
  }

  deleteTag(group: Group): void {
    if (!confirm(`Delete group "${group.name}"? This will remove it from all athletes.`)) return;
    this.groupService.deleteGroup(group.id).subscribe({
      next: () => this.loadGroups(),
      error: () => {},
    });
  }

  // Open invite modal filtered to this group
  openInviteModal(group: Group): void {
    this.selectedTagForInvite = group;
    this.isInviteModalOpen = true;
  }

  // Navigate to coach page for a specific athlete
  navigateToCoach(athleteId: string): void {
    this.router.navigate(['/coach'], { queryParams: { athleteId } });
  }
}
