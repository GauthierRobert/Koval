import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  ClubDetail,
  ClubGroup,
  ClubInviteCode,
  ClubService,
  ClubTrainingSession,
  GroupLinkedTraining,
  getEffectiveLinkedTrainings,
} from '../../../../services/club.service';
import {ClubSessionService} from '../../../../services/club-session.service';
import {ClubFeedService} from '../../../../services/club-feed.service';
import {AuthService} from '../../../../services/auth.service';
import {ClubFeedTabComponent} from './tabs/club-feed-tab/club-feed-tab.component';
import {ClubSessionsTabComponent} from './tabs/club-sessions-tab/club-sessions-tab.component';
import {ClubMembersTabComponent} from './tabs/club-members-tab/club-members-tab.component';
import {ClubStatsTabComponent} from './tabs/club-stats-tab/club-stats-tab.component';
import {ClubLeaderboardTabComponent} from './tabs/club-leaderboard-tab/club-leaderboard-tab.component';
import {ClubRaceGoalsTabComponent} from './tabs/club-race-goals-tab/club-race-goals-tab.component';
import {ClubOpenSessionsTabComponent} from './tabs/club-open-sessions-tab/club-open-sessions-tab.component';
import {ClubChatTabComponent} from './tabs/club-chat-tab/club-chat-tab.component';
import {TrainingActionModalComponent} from '../../../shared/training-action-modal/training-action-modal.component';
import {ActionContext} from '../../../../services/ai-action.service';
import {BehaviorSubject, map, Observable, Subscription} from 'rxjs';

type TabId = 'feed' | 'sessions' | 'open-sessions' | 'members' | 'stats' | 'leaderboard' | 'race-goals' | 'chat';

@Component({
  selector: 'app-club-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    TranslateModule,
    ClubFeedTabComponent,
    ClubSessionsTabComponent,
    ClubMembersTabComponent,
    ClubStatsTabComponent,
    ClubLeaderboardTabComponent,
    ClubRaceGoalsTabComponent,
    ClubOpenSessionsTabComponent,
    ClubChatTabComponent,
    TrainingActionModalComponent,
  ],
  templateUrl: './club-detail-page.component.html',
  styleUrl: './club-detail-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubDetailPageComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private clubFeedService = inject(ClubFeedService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  selectedClub$ = this.clubService.selectedClub$;
  currentUserId: string | null = null;

  activeTab: TabId = 'feed';
  loadedTabs = new Set<TabId>();

  clubId = '';
  showAiModal = false;
  aiContext: ActionContext = {};
  aiSessionInfo: { scheduledAt?: string; sport?: string; clubGroupName?: string } | null = null;
  private aiSessionDate: string | undefined;
  aiExistingLinkedTrainings: GroupLinkedTraining[] = [];
  aiSessionGroupId?: string;
  private clubGroups: ClubGroup[] = [];
  private subs = new Subscription();

  copiedClubCodeId: string | null = null;
  readonly isJoiningClub$ = new BehaviorSubject(false);
  readonly isLeavingClub$ = new BehaviorSubject(false);
  clubInviteCode$: Observable<ClubInviteCode | null> = this.clubService.inviteCodes$.pipe(
    map((codes) => codes.find((c) => c.active && !c.clubGroupId) ?? null),
  );

  readonly tabs: Array<{ id: TabId; label: string; shortLabel: string }> = [
    { id: 'feed', label: 'CLUB_DETAIL.TAB_FEED', shortLabel: 'CLUB_DETAIL.TAB_FEED_SHORT' },
    { id: 'open-sessions', label: 'CLUB_DETAIL.TAB_SESSIONS', shortLabel: 'CLUB_DETAIL.TAB_SESSIONS_SHORT' },
    { id: 'sessions', label: 'CLUB_DETAIL.TAB_RECURRING_SESSIONS', shortLabel: 'CLUB_DETAIL.TAB_RECURRING_SESSIONS_SHORT' },
    { id: 'members', label: 'CLUB_DETAIL.TAB_MEMBERS', shortLabel: 'CLUB_DETAIL.TAB_MEMBERS_SHORT' },
    { id: 'stats', label: 'CLUB_DETAIL.TAB_STATS', shortLabel: 'CLUB_DETAIL.TAB_STATS_SHORT' },
    { id: 'leaderboard', label: 'CLUB_DETAIL.TAB_LEADERBOARD', shortLabel: 'CLUB_DETAIL.TAB_LEADERBOARD_SHORT' },
    { id: 'race-goals', label: 'CLUB_DETAIL.TAB_RACE_GOALS', shortLabel: 'CLUB_DETAIL.TAB_RACE_GOALS_SHORT' },
    { id: 'chat', label: 'CLUB_DETAIL.TAB_CHAT', shortLabel: 'CLUB_DETAIL.TAB_CHAT_SHORT' },
  ];

  ngOnInit(): void {
    this.subs.add(
      this.authService.user$.subscribe((u) => {
        this.currentUserId = u?.id ?? null;
        this.cdr.markForCheck();
      })
    );

    this.subs.add(
      this.route.params.subscribe((params) => {
        this.clubId = params['id'];
        this.loadedTabs.clear();
        this.activeTab = 'feed';
        this.clubService.resetDetail();
        this.clubSessionService.resetDetail();
        this.clubFeedService.resetDetail();
        this.clubService.loadClubDetail(this.clubId);
        this.activateTab('feed');
      })
    );

    // Load invite codes early for header display (gated by role in template)
    this.subs.add(
      this.selectedClub$.subscribe((club) => {
        if (club && this.canManageInvites(club)) {
          this.clubService.loadInviteCodes(club.id);
        }
      })
    );

    this.subs.add(
      this.clubService.groups$.subscribe((groups) => {
        this.clubGroups = groups;
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.clubService.resetDetail();
    this.clubSessionService.resetDetail();
    this.clubFeedService.resetDetail();
  }

  activateTab(tab: TabId): void {
    this.activeTab = tab;
    if (this.loadedTabs.has(tab)) return;
    this.loadedTabs.add(tab);

    switch (tab) {
      case 'feed':
        // Feed tab loads its own data via the component
        break;
      case 'sessions':
        this.clubSessionService.loadRecurringTemplates(this.clubId);
        this.clubService.loadMembers(this.clubId);
        break;
      case 'open-sessions':
        this.clubService.loadMembers(this.clubId);
        this.clubService.loadGroups(this.clubId);
        break;
      case 'members':
        this.clubService.loadMembers(this.clubId);
        this.clubService.loadGroups(this.clubId);
        break;
      case 'stats':
        this.clubFeedService.loadExtendedStats(this.clubId);
        break;
      case 'leaderboard':
        this.clubFeedService.loadLeaderboard(this.clubId);
        break;
      case 'race-goals':
        this.clubFeedService.loadRaceGoals(this.clubId);
        break;
      case 'chat':
        // The embedded chat component resolves the room itself on first render.
        break;
    }
    this.cdr.markForCheck();
  }

  join(club: ClubDetail): void {
    this.isJoiningClub$.next(true);
    this.clubService.joinClub(club.id).subscribe({
      error: () => this.isJoiningClub$.next(false),
      complete: () => this.isJoiningClub$.next(false),
    });
  }

  leave(club: ClubDetail): void {
    if (club.currentMemberRole === 'OWNER') return;
    this.isLeavingClub$.next(true);
    this.clubService.leaveClub(club.id).subscribe({
      error: () => this.isLeavingClub$.next(false),
      complete: () => this.isLeavingClub$.next(false),
    });
  }

  goBack(): void {
    this.router.navigate(['/clubs']);
  }

  hasPendingBadge(tab: TabId): boolean {
    return tab === 'members';
  }

  openAiModalForSession(session: ClubTrainingSession): void {
    this.aiContext = { clubId: this.clubId, sessionId: session.id };
    const groupName = session.clubGroupId
      ? this.clubGroups.find((g) => g.id === session.clubGroupId)?.name
      : undefined;
    this.aiSessionInfo = {
      scheduledAt: session.scheduledAt
        ? new Date(session.scheduledAt).toLocaleString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          })
        : undefined,
      sport: session.sport,
      clubGroupName: groupName,
    };
    this.aiSessionDate = session.scheduledAt;
    this.aiExistingLinkedTrainings = getEffectiveLinkedTrainings(session);
    this.aiSessionGroupId = session.clubGroupId || undefined;
    this.showAiModal = true;
    this.cdr.markForCheck();
  }

  onAiCreated(_result: { success: boolean; content?: string }): void {
    this.showAiModal = false;
    this.aiSessionInfo = null;
    // Reload sessions for the week containing the session so the card reflects the new training
    const refDate = this.aiSessionDate ? new Date(this.aiSessionDate) : new Date();
    const monday = this.getMonday(refDate);
    const from = monday.toISOString();
    const to = new Date(monday.getTime() + 7 * 86400000).toISOString();
    this.clubSessionService.loadSessionsForRange(this.clubId, from, to);
    this.aiSessionDate = undefined;
    this.cdr.markForCheck();
  }

  private getMonday(d: Date): Date {
    const date = new Date(d);
    const day = date.getDay();
    const diff = date.getDate() - day + (day === 0 ? -6 : 1);
    date.setDate(diff);
    date.setHours(0, 0, 0, 0);
    return date;
  }

  getMembershipLabel(status: string | undefined): string {
    if (!status) return '';
    if (status === 'ACTIVE') return this.translate.instant('CLUB_DETAIL.MEMBERSHIP_MEMBER');
    if (status === 'PENDING') return this.translate.instant('CLUB_DETAIL.MEMBERSHIP_PENDING');
    return status;
  }

  canManageInvites(club: ClubDetail): boolean {
    const role = club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  copyClubInviteLink(code: string, codeId: string): void {
    const link = `${window.location.origin}/clubs/join/${code}`;
    navigator.clipboard.writeText(link).then(() => {
      this.copiedClubCodeId = codeId;
      this.cdr.markForCheck();
      setTimeout(() => {
        this.copiedClubCodeId = null;
        this.cdr.markForCheck();
      }, 2000);
    });
  }
}
