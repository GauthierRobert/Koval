import { ChangeDetectionStrategy, Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ClubDetail,
  ClubMemberRole,
  ClubService,
  ClubGroup,
  ClubInviteCode,
} from '../../../../../../services/club.service';

@Component({
  selector: 'app-club-members-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  inviteCodes$ = this.clubService.inviteCodes$;

  newTagName = '';
  tagPanelOpen = false;
  invitePanelOpen = false;
  roleChangeInProgress = new Set<string>();
  newInviteGroupId = '';
  newInviteMaxUses = 0;
  copiedCodeId: string | null = null;

  get isAdmin(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN';
  }

  get isOwner(): boolean {
    return this.club?.currentMemberRole === 'OWNER';
  }

  get canManageInvites(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  ngOnInit(): void {
    if (this.isAdmin) {
      this.clubService.loadPendingRequests(this.club.id);
    }
    if (this.canManageInvites) {
      this.clubService.loadInviteCodes(this.club.id);
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

  generateInviteCode(): void {
    this.clubService
      .generateInviteCode(
        this.club.id,
        this.newInviteGroupId || undefined,
        this.newInviteMaxUses,
      )
      .subscribe({
        next: () => {
          this.newInviteGroupId = '';
          this.newInviteMaxUses = 0;
        },
        error: () => {},
      });
  }

  deactivateInviteCode(codeId: string): void {
    this.clubService.deactivateInviteCode(this.club.id, codeId).subscribe({ error: () => {} });
  }

  copyInviteCode(code: string, codeId: string): void {
    navigator.clipboard.writeText(code).then(() => {
      this.copiedCodeId = codeId;
      setTimeout(() => (this.copiedCodeId = null), 2000);
    });
  }

  isExpired(expiresAt: string | undefined): boolean {
    if (!expiresAt) return false;
    return new Date(expiresAt) < new Date();
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
