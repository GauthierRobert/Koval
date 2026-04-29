import {inject, Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {
  ClubDetail,
  ClubGroup,
  ClubInviteCode,
  ClubMember,
  ClubMemberRole,
  ClubMembership,
  ClubSummary,
  CreateClubData,
  MyClubRoleEntry,
} from '../models/club.model';

// Re-export all types from the model for backwards compatibility
export * from '../models/club.model';

@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);

  private userClubsSubject = new BehaviorSubject<ClubSummary[]>([]);
  userClubs$ = this.userClubsSubject.asObservable();

  private userClubsLoadingSubject = new BehaviorSubject<boolean>(true);
  userClubsLoading$ = this.userClubsLoadingSubject.asObservable();

  private selectedClubSubject = new BehaviorSubject<ClubDetail | null>(null);
  selectedClub$ = this.selectedClubSubject.asObservable();

  private membersSubject = new BehaviorSubject<ClubMember[]>([]);
  members$ = this.membersSubject.asObservable();

  private pendingSubject = new BehaviorSubject<ClubMember[]>([]);
  pending$ = this.pendingSubject.asObservable();

  private groupsSubject = new BehaviorSubject<ClubGroup[]>([]);
  groups$ = this.groupsSubject.asObservable();

  private myClubRolesSubject = new BehaviorSubject<MyClubRoleEntry[]>([]);
  myClubRoles$ = this.myClubRolesSubject.asObservable();

  private inviteCodesSubject = new BehaviorSubject<ClubInviteCode[]>([]);
  inviteCodes$ = this.inviteCodesSubject.asObservable();

  // --- Club CRUD ---

  loadUserClubs(): void {
    this.userClubsLoadingSubject.next(true);
    this.http
      .get<ClubSummary[]>(this.apiUrl)
      .pipe(catchError(() => of([] as ClubSummary[])))
      .subscribe((clubs) => this.ngZone.run(() => {
        this.userClubsSubject.next(clubs);
        this.userClubsLoadingSubject.next(false);
      }));
  }

  browsePublicClubs(page = 0): Observable<ClubSummary[]> {
    return this.http
      .get<ClubSummary[]>(`${this.apiUrl}/public`, { params: { page: page.toString(), size: '20' } })
      .pipe(catchError(() => of([] as ClubSummary[])));
  }

  createClub(data: CreateClubData): Observable<ClubSummary> {
    return new Observable((observer) => {
      this.http.post<ClubSummary>(this.apiUrl, data).subscribe({
        next: (club) => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            observer.next(club);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadClubDetail(id: string): void {
    this.selectedClubSubject.next(null);
    this.http
      .get<ClubDetail>(`${this.apiUrl}/${id}`)
      .pipe(catchError(() => of(null as ClubDetail | null)))
      .subscribe((club) => this.ngZone.run(() => this.selectedClubSubject.next(club)));
  }

  getClubDetail(id: string): Observable<ClubDetail | null> {
    return this.http
      .get<ClubDetail>(`${this.apiUrl}/${id}`)
      .pipe(catchError(() => of(null as ClubDetail | null)));
  }

  // --- Membership ---

  joinClub(id: string): Observable<void> {
    return new Observable((observer) => {
      this.http.post<void>(`${this.apiUrl}/${id}/join`, {}).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            this.loadClubDetail(id);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  leaveClub(id: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${id}/leave`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            this.loadClubDetail(id);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadMembers(id: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${id}/members`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((members) => this.ngZone.run(() => this.membersSubject.next(members)));
  }

  loadPendingRequests(id: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${id}/members/pending`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((pending) => this.ngZone.run(() => this.pendingSubject.next(pending)));
  }

  approveMember(clubId: string, membershipId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.post<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/approve`, {}).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadMembers(clubId);
            this.loadPendingRequests(clubId);
            this.loadClubDetail(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  rejectMember(clubId: string, membershipId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/reject`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadPendingRequests(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  updateMemberRole(clubId: string, membershipId: string, role: ClubMemberRole): Observable<void> {
    return new Observable((observer) => {
      this.http.put<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/role`, { role }).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadMembers(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  loadMyClubRoles(): void {
    this.http
      .get<MyClubRoleEntry[]>(`${this.apiUrl}/my-roles`)
      .pipe(catchError(() => of([] as MyClubRoleEntry[])))
      .subscribe((roles) => this.ngZone.run(() => this.myClubRolesSubject.next(roles)));
  }

  // --- Groups ---

  getClubGroups(clubId: string): Observable<ClubGroup[]> {
    return this.http
      .get<ClubGroup[]>(`${this.apiUrl}/${clubId}/groups`)
      .pipe(catchError(() => of([] as ClubGroup[])));
  }

  loadGroups(clubId: string): void {
    this.http
      .get<ClubGroup[]>(`${this.apiUrl}/${clubId}/groups`)
      .pipe(catchError(() => of([] as ClubGroup[])))
      .subscribe((groups) => this.ngZone.run(() => this.groupsSubject.next(groups)));
  }

  createGroup(clubId: string, name: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups`, { name }).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  deleteGroup(clubId: string, groupId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/groups/${groupId}`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  addMemberToGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`, {}).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  removeMemberFromGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return new Observable((observer) => {
      this.http.delete<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`).subscribe({
        next: (group) => {
          this.ngZone.run(() => {
            this.loadGroups(clubId);
            this.loadMembers(clubId);
            observer.next(group);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  joinGroupSelf(clubId: string, groupId: string): Observable<ClubGroup> {
    return this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/join`, {});
  }

  leaveGroupSelf(clubId: string, groupId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/groups/${groupId}/leave`);
  }

  // --- Invite Codes ---

  loadInviteCodes(clubId: string): void {
    this.http
      .get<ClubInviteCode[]>(`${this.apiUrl}/${clubId}/invite-codes`)
      .pipe(catchError(() => of([] as ClubInviteCode[])))
      .subscribe((codes) => this.ngZone.run(() => this.inviteCodesSubject.next(codes)));
  }

  generateInviteCode(
    clubId: string,
    clubGroupId?: string,
    maxUses = 0,
    expiresAt?: string,
  ): Observable<ClubInviteCode> {
    return new Observable((observer) => {
      this.http
        .post<ClubInviteCode>(`${this.apiUrl}/${clubId}/invite-codes`, {
          clubGroupId: clubGroupId || null,
          maxUses,
          expiresAt: expiresAt || null,
        })
        .subscribe({
          next: (code) => {
            this.ngZone.run(() => {
              this.loadInviteCodes(clubId);
              observer.next(code);
              observer.complete();
            });
          },
          error: (err) => observer.error(err),
        });
    });
  }

  deactivateInviteCode(clubId: string, codeId: string): Observable<void> {
    return new Observable((observer) => {
      this.http.delete<void>(`${this.apiUrl}/${clubId}/invite-codes/${codeId}`).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loadInviteCodes(clubId);
            observer.next();
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  redeemClubInviteCode(code: string): Observable<ClubMembership> {
    return new Observable((observer) => {
      this.http.post<ClubMembership>(`${this.apiUrl}/redeem-invite`, { code }).subscribe({
        next: (membership) => {
          this.ngZone.run(() => {
            this.loadUserClubs();
            observer.next(membership);
            observer.complete();
          });
        },
        error: (err) => observer.error(err),
      });
    });
  }

  resetDetail(): void {
    this.selectedClubSubject.next(null);
    this.membersSubject.next([]);
    this.pendingSubject.next([]);
    this.groupsSubject.next([]);
    this.inviteCodesSubject.next([]);
  }
}
