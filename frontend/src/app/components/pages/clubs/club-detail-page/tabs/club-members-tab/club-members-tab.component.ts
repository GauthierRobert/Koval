import { ChangeDetectionStrategy, Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ClubDetail,
  ClubMember,
  ClubMemberRole,
  ClubService,
  ClubGroup,
} from '../../../../../../services/club.service';
import { User } from '../../../../../../services/auth.service';
import { ScheduleModalComponent } from '../../../../../shared/schedule-modal/schedule-modal.component';

@Component({
  selector: 'app-club-members-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, ScheduleModalComponent],
  templateUrl: './club-members-tab.component.html',
  styleUrl: './club-members-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubMembersTabComponent implements OnInit {
  @Input() club!: ClubDetail;

  private clubService = inject(ClubService);
  members$ = this.clubService.members$;
  pending$ = this.clubService.pending$;
  tags$ = this.clubService.groups$;

  newTagName = '';
  roleChangeInProgress = new Set<string>();

  isScheduleModalOpen = false;
  assigningAthletes: User[] = [];

  get canAssign(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  get isAdmin(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN';
  }

  get isOwner(): boolean {
    return this.club?.currentMemberRole === 'OWNER';
  }

  get canManageGroups(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  ngOnInit(): void {
    if (this.isAdmin) {
      this.clubService.loadPendingRequests(this.club.id);
    }
  }

  approve(membershipId: string | undefined): void {
    if (!membershipId) return;
    this.clubService.approveMember(this.club.id, membershipId).subscribe({ error: () => {} });
  }

  reject(membershipId: string | undefined): void {
    if (!membershipId) return;
    this.clubService.rejectMember(this.club.id, membershipId).subscribe({ error: () => {} });
  }

  createTag(): void {
    const name = this.newTagName.trim();
    if (!name) return;
    this.clubService.createGroup(this.club.id, name).subscribe({
      next: () => {
        this.newTagName = '';
      },
      error: () => {},
    });
  }

  deleteTag(groupId: string): void {
    this.clubService.deleteGroup(this.club.id, groupId).subscribe({ error: () => {} });
  }

  addToTag(groupId: string, userId: string): void {
    if (!groupId) return;
    this.clubService.addMemberToGroup(this.club.id, groupId, userId).subscribe({ error: () => {} });
  }

  removeFromTag(groupId: string | undefined, userId: string): void {
    if (!groupId) return;
    this.clubService.removeMemberFromGroup(this.club.id, groupId, userId).subscribe({ error: () => {} });
  }

  changeRole(membershipId: string | undefined, role: string): void {
    if (!membershipId || this.roleChangeInProgress.has(membershipId)) return;
    this.roleChangeInProgress.add(membershipId);
    this.clubService.updateMemberRole(this.club.id, membershipId, role as ClubMemberRole).subscribe({
      next: () => this.roleChangeInProgress.delete(membershipId),
      error: () => this.roleChangeInProgress.delete(membershipId),
    });
  }

  canChangeRole(memberRole: string): boolean {
    if (memberRole === 'OWNER') return false;
    if (this.isOwner) return true;
    if (this.isAdmin && memberRole !== 'ADMIN') return true;
    return false;
  }

  getAvailableRoles(): string[] {
    if (this.isOwner) return ['ADMIN', 'COACH', 'MEMBER'];
    return ['COACH', 'MEMBER'];
  }


  assignToClubGroup(tag: ClubGroup): void {
    this.assigningAthletes = tag.memberIds.map(id => ({ id, displayName: id } as User));
    this.isScheduleModalOpen = true;
  }

  assignToAllMembers(members: ClubMember[]): void {
    this.assigningAthletes = members.map(m => ({
      id: m.userId,
      displayName: m.displayName || m.userId,
    } as User));
    this.isScheduleModalOpen = true;
  }

  onScheduled(): void {
    this.isScheduleModalOpen = false;
    this.assigningAthletes = [];
  }

  getAvailableTags(member: { userId: string }, allGroups: ClubGroup[]): ClubGroup[] {
    return allGroups.filter((g) => !g.memberIds.includes(member.userId));
  }

  getTagIdByName(name: string, allGroups: ClubGroup[]): string | undefined {
    return allGroups.find((g) => g.name === name)?.id;
  }

  getRoleBadgeClass(role: string): string {
    if (role === 'OWNER') return 'badge-owner';
    if (role === 'ADMIN') return 'badge-admin';
    if (role === 'COACH') return 'badge-coach';
    return 'badge-member';
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }
}
