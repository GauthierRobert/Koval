import { ChangeDetectionStrategy, Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClubDetail, ClubMemberRole, ClubService, ClubTag } from '../../../../../../services/club.service';

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
  tags$ = this.clubService.tags$;

  newTagName = '';
  tagPanelOpen = false;
  roleChangeInProgress = new Set<string>();

  get isAdmin(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN';
  }

  get isOwner(): boolean {
    return this.club?.currentMemberRole === 'OWNER';
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
    this.clubService.createTag(this.club.id, name).subscribe({
      next: () => {
        this.newTagName = '';
      },
      error: () => {},
    });
  }

  deleteTag(tagId: string): void {
    this.clubService.deleteTag(this.club.id, tagId).subscribe({ error: () => {} });
  }

  addToTag(tagId: string, userId: string): void {
    if (!tagId) return;
    this.clubService.addMemberToTag(this.club.id, tagId, userId).subscribe({ error: () => {} });
  }

  removeFromTag(tagId: string | undefined, userId: string): void {
    if (!tagId) return;
    this.clubService.removeMemberFromTag(this.club.id, tagId, userId).subscribe({ error: () => {} });
  }

  changeRole(membershipId: string | undefined, role: string): void {
    if (!membershipId || this.roleChangeInProgress.has(membershipId)) return;
    this.roleChangeInProgress.add(membershipId);
    this.clubService.updateMemberRole(this.club.id, membershipId, role as ClubMemberRole).subscribe({
      next: () => this.roleChangeInProgress.delete(membershipId),
      error: () => this.roleChangeInProgress.delete(membershipId),
    });
  }

  getAvailableTags(member: { userId: string }, allTags: ClubTag[]): ClubTag[] {
    return allTags.filter((t) => !t.memberIds.includes(member.userId));
  }

  getTagIdByName(name: string, allTags: ClubTag[]): string | undefined {
    return allTags.find((t) => t.name === name)?.id;
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
