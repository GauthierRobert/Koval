import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {
  ClubDetail,
  ClubGroup,
  ClubInviteCode,
  ClubMemberRole,
  ClubMembership,
  ClubSummary,
  CreateClubData,
} from '../models/club.model';
import {ClubCrudService} from './club/club-crud.service';
import {ClubGroupService} from './club/club-group.service';
import {ClubInviteService} from './club/club-invite.service';
import {ClubMembershipService} from './club/club-membership.service';

// Re-export model types for backwards compatibility.
export * from '../models/club.model';

// Re-export the focused services so new code can depend on them directly.
export {ClubCrudService} from './club/club-crud.service';
export {ClubGroupService} from './club/club-group.service';
export {ClubInviteService} from './club/club-invite.service';
export {ClubMembershipService} from './club/club-membership.service';

/**
 * Facade over the focused club services. Delegates each method to its owning service
 * so existing consumers keep working while new code can inject the focused services.
 */
@Injectable({providedIn: 'root'})
export class ClubService {
  private crud = inject(ClubCrudService);
  private membership = inject(ClubMembershipService);
  private groups = inject(ClubGroupService);
  private invites = inject(ClubInviteService);

  // --- State streams ---
  readonly userClubs$ = this.crud.userClubs$;
  readonly userClubsLoading$ = this.crud.userClubsLoading$;
  readonly selectedClub$ = this.crud.selectedClub$;
  readonly myClubRoles$ = this.crud.myClubRoles$;
  readonly members$ = this.membership.members$;
  readonly pending$ = this.membership.pending$;
  readonly groups$ = this.groups.groups$;
  readonly inviteCodes$ = this.invites.inviteCodes$;

  // --- Club CRUD ---
  loadUserClubs(): void {
    this.crud.loadUserClubs();
  }
  browsePublicClubs(page = 0): Observable<ClubSummary[]> {
    return this.crud.browsePublicClubs(page);
  }
  createClub(data: CreateClubData): Observable<ClubSummary> {
    return this.crud.createClub(data);
  }
  loadClubDetail(id: string): void {
    this.crud.loadClubDetail(id);
  }
  getClubDetail(id: string): Observable<ClubDetail | null> {
    return this.crud.getClubDetail(id);
  }
  loadMyClubRoles(): void {
    this.crud.loadMyClubRoles();
  }

  // --- Membership ---
  joinClub(id: string): Observable<void> {
    return this.membership.joinClub(id);
  }
  leaveClub(id: string): Observable<void> {
    return this.membership.leaveClub(id);
  }
  loadMembers(id: string): void {
    this.membership.loadMembers(id);
  }
  loadPendingRequests(id: string): void {
    this.membership.loadPendingRequests(id);
  }
  approveMember(clubId: string, membershipId: string): Observable<void> {
    return this.membership.approveMember(clubId, membershipId);
  }
  rejectMember(clubId: string, membershipId: string): Observable<void> {
    return this.membership.rejectMember(clubId, membershipId);
  }
  updateMemberRole(
    clubId: string,
    membershipId: string,
    role: ClubMemberRole,
  ): Observable<void> {
    return this.membership.updateMemberRole(clubId, membershipId, role);
  }

  // --- Groups ---
  getClubGroups(clubId: string): Observable<ClubGroup[]> {
    return this.groups.getClubGroups(clubId);
  }
  loadGroups(clubId: string): void {
    this.groups.loadGroups(clubId);
  }
  createGroup(clubId: string, name: string): Observable<ClubGroup> {
    return this.groups.createGroup(clubId, name);
  }
  deleteGroup(clubId: string, groupId: string): Observable<void> {
    return this.groups.deleteGroup(clubId, groupId);
  }
  addMemberToGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return this.groups.addMemberToGroup(clubId, groupId, userId);
  }
  removeMemberFromGroup(
    clubId: string,
    groupId: string,
    userId: string,
  ): Observable<ClubGroup> {
    return this.groups.removeMemberFromGroup(clubId, groupId, userId);
  }
  joinGroupSelf(clubId: string, groupId: string): Observable<ClubGroup> {
    return this.groups.joinGroupSelf(clubId, groupId);
  }
  leaveGroupSelf(clubId: string, groupId: string): Observable<void> {
    return this.groups.leaveGroupSelf(clubId, groupId);
  }

  // --- Invite Codes ---
  loadInviteCodes(clubId: string): void {
    this.invites.loadInviteCodes(clubId);
  }
  generateInviteCode(
    clubId: string,
    clubGroupId?: string,
    maxUses = 0,
    expiresAt?: string,
  ): Observable<ClubInviteCode> {
    return this.invites.generateInviteCode(clubId, clubGroupId, maxUses, expiresAt);
  }
  deactivateInviteCode(clubId: string, codeId: string): Observable<void> {
    return this.invites.deactivateInviteCode(clubId, codeId);
  }
  redeemClubInviteCode(code: string): Observable<ClubMembership> {
    return this.invites.redeemClubInviteCode(code);
  }

  resetDetail(): void {
    this.crud.resetSelected();
    this.membership.reset();
    this.groups.reset();
    this.invites.reset();
  }
}
