import {ChangeDetectionStrategy, Component, DestroyRef, EventEmitter, inject, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BehaviorSubject, forkJoin, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ClubGroup, ClubService, ClubSummary} from '../../../services/club.service';
import {Group, GroupService} from '../../../services/group.service';
import {CoachService} from '../../../services/coach.service';
import {AuthService} from '../../../services/auth.service';

interface ClubGroupWithMembership {
  group: ClubGroup;
  isMember: boolean;
}

interface ClubWithGroups {
  club: ClubSummary;
  groups: ClubGroupWithMembership[];
}

@Component({
  selector: 'app-memberships-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './memberships-modal.component.html',
  styleUrl: './memberships-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MembershipsModalComponent implements OnInit {
  @Output() closed = new EventEmitter<void>();

  private clubService = inject(ClubService);
  private groupService = inject(GroupService);
  private coachService = inject(CoachService);
  private authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);

  inviteCode = '';
  private messageSubject = new BehaviorSubject<string>('');
  message$ = this.messageSubject.asObservable();
  private messageTypeSubject = new BehaviorSubject<'success' | 'error'>('success');
  messageType$ = this.messageTypeSubject.asObservable();

  private clubsWithGroupsSubject = new BehaviorSubject<ClubWithGroups[]>([]);
  clubsWithGroups$ = this.clubsWithGroupsSubject.asObservable();

  private coachGroupsSubject = new BehaviorSubject<Group[]>([]);
  coachGroups$ = this.coachGroupsSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(true);
  loading$ = this.loadingSubject.asObservable();

  private currentUserId = '';

  ngOnInit(): void {
    const user = this.authService.currentUser;
    if (user) {
      this.currentUserId = user.id;
    }
    this.loadAll();
  }

  private loadAll(): void {
    this.loadingSubject.next(true);
    this.clubService.loadUserClubs();
    this.loadCoachGroups();

    this.clubService.userClubs$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((clubs) => {
      if (clubs.length === 0) {
        this.clubsWithGroupsSubject.next([]);
        this.loadingSubject.next(false);
        return;
      }
      const groupRequests = clubs.map((club) =>
        this.clubService.getClubGroups(club.id).pipe(catchError(() => of([] as ClubGroup[])))
      );
      forkJoin(groupRequests).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((allGroups) => {
        const result: ClubWithGroups[] = clubs.map((club, i) => ({
          club,
          groups: allGroups[i].map((g) => ({
            group: g,
            isMember: !!(g.memberIds && g.memberIds.includes(this.currentUserId)),
          })),
        }));
        this.clubsWithGroupsSubject.next(result);
        this.loadingSubject.next(false);
      });
    });
  }

  private loadCoachGroups(): void {
    this.groupService.getGroups().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((groups) => {
      this.coachGroupsSubject.next(groups);
    });
  }

  redeemCode(): void {
    const code = this.inviteCode.trim();
    if (!code) return;
    this.coachService.redeemInviteCode(code).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.messageSubject.next(this.translate.instant('MEMBERSHIPS.MSG_JOINED_SUCCESSFULLY'));
        this.messageTypeSubject.next('success');
        this.inviteCode = '';
        this.authService.refreshUser();
        this.loadAll();
      },
      error: () => {
        this.messageSubject.next(this.translate.instant('MEMBERSHIPS.MSG_INVALID_CODE'));
        this.messageTypeSubject.next('error');
      },
    });
  }

  leaveClub(clubId: string): void {
    this.clubService.leaveClub(clubId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.clubService.loadUserClubs();
    });
  }

  joinClubGroup(clubId: string, groupId: string): void {
    this.clubService.joinGroupSelf(clubId, groupId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reloadClubGroups(clubId));
  }

  leaveClubGroup(clubId: string, groupId: string): void {
    this.clubService.leaveGroupSelf(clubId, groupId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reloadClubGroups(clubId));
  }

  private reloadClubGroups(clubId: string): void {
    this.clubService.getClubGroups(clubId).pipe(
      catchError(() => of([] as ClubGroup[])),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((groups) => {
      const current = this.clubsWithGroupsSubject.value;
      const updated = current.map((cwg) =>
        cwg.club.id === clubId
          ? {
              ...cwg,
              groups: groups.map((g) => ({
                group: g,
                isMember: !!(g.memberIds && g.memberIds.includes(this.currentUserId)),
              })),
            }
          : cwg
      );
      this.clubsWithGroupsSubject.next(updated);
    });
  }

  leaveCoachGroup(groupId: string): void {
    this.groupService.leaveGroup(groupId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadCoachGroups();
      this.authService.refreshUser();
    });
  }

  isOwner(club: ClubSummary): boolean {
    return !!club.membershipStatus && club.membershipStatus.includes('OWNER');
  }

  getRoleBadge(club: ClubSummary): string {
    if (!club.membershipStatus) return this.translate.instant('MEMBERSHIPS.ROLE_MEMBER');
    if (club.membershipStatus.includes('OWNER')) return this.translate.instant('MEMBERSHIPS.ROLE_OWNER');
    if (club.membershipStatus.includes('ADMIN')) return this.translate.instant('MEMBERSHIPS.ROLE_ADMIN');
    if (club.membershipStatus.includes('COACH')) return this.translate.instant('MEMBERSHIPS.ROLE_COACH');
    return this.translate.instant('MEMBERSHIPS.ROLE_MEMBER');
  }

  onBackdropClick(): void {
    this.closed.emit();
  }
}
