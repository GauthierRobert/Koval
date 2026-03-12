import { ChangeDetectionStrategy, Component, EventEmitter, inject, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ClubService, ClubGroup, ClubSummary } from '../../../services/club.service';
import { GroupService, Group } from '../../../services/group.service';
import { CoachService } from '../../../services/coach.service';
import { AuthService } from '../../../services/auth.service';

interface ClubWithGroups {
  club: ClubSummary;
  groups: ClubGroup[];
}

@Component({
  selector: 'app-memberships-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

    this.clubService.userClubs$.subscribe((clubs) => {
      if (clubs.length === 0) {
        this.clubsWithGroupsSubject.next([]);
        this.loadingSubject.next(false);
        return;
      }
      const groupRequests = clubs.map((club) =>
        this.clubService.getClubGroups(club.id).pipe(catchError(() => of([] as ClubGroup[])))
      );
      forkJoin(groupRequests).subscribe((allGroups) => {
        const result: ClubWithGroups[] = clubs.map((club, i) => ({
          club,
          groups: allGroups[i].filter(
            (g) => g.memberIds && g.memberIds.includes(this.currentUserId)
          ),
        }));
        this.clubsWithGroupsSubject.next(result);
        this.loadingSubject.next(false);
      });
    });
  }

  private loadCoachGroups(): void {
    this.groupService.getGroups().subscribe((groups) => {
      this.coachGroupsSubject.next(groups);
    });
  }

  redeemCode(): void {
    const code = this.inviteCode.trim();
    if (!code) return;
    this.coachService.redeemInviteCode(code).subscribe({
      next: () => {
        this.messageSubject.next('Joined successfully!');
        this.messageTypeSubject.next('success');
        this.inviteCode = '';
        this.authService.refreshUser();
        this.loadCoachGroups();
        this.clubService.loadUserClubs();
      },
      error: () => {
        this.messageSubject.next('Invalid or expired invite code.');
        this.messageTypeSubject.next('error');
      },
    });
  }

  leaveClub(clubId: string): void {
    this.clubService.leaveClub(clubId).subscribe(() => {
      this.clubService.loadUserClubs();
    });
  }

  leaveClubGroup(clubId: string, groupId: string): void {
    this.clubService.removeMemberFromGroup(clubId, groupId, this.currentUserId).subscribe(() => {
      // Reload club groups
      this.clubService.getClubGroups(clubId).pipe(catchError(() => of([] as ClubGroup[]))).subscribe((groups) => {
        const current = this.clubsWithGroupsSubject.value;
        const updated = current.map((cwg) =>
          cwg.club.id === clubId
            ? { ...cwg, groups: groups.filter((g) => g.memberIds?.includes(this.currentUserId)) }
            : cwg
        );
        this.clubsWithGroupsSubject.next(updated);
      });
    });
  }

  leaveCoachGroup(groupId: string): void {
    this.groupService.leaveGroup(groupId).subscribe(() => {
      this.loadCoachGroups();
      this.authService.refreshUser();
    });
  }

  isOwner(club: ClubSummary): boolean {
    return !!club.membershipStatus && club.membershipStatus.includes('OWNER');
  }

  getRoleBadge(club: ClubSummary): string {
    if (!club.membershipStatus) return 'MEMBER';
    if (club.membershipStatus.includes('OWNER')) return 'OWNER';
    if (club.membershipStatus.includes('ADMIN')) return 'ADMIN';
    if (club.membershipStatus.includes('COACH')) return 'COACH';
    return 'MEMBER';
  }

  onBackdropClick(): void {
    this.closed.emit();
  }
}
