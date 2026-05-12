import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, OnDestroy, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  canManageClub,
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
import {ClubChatTabComponent} from './tabs/club-chat-tab/club-chat-tab.component';
import {ClubGazetteTabComponent} from './tabs/club-gazette-tab/club-gazette-tab.component';
import {ClubTestsTabComponent} from './tabs/club-tests-tab/club-tests-tab.component';
import {ClubTestService} from '../../../../services/club-test.service';
import {TrainingActionModalComponent} from '../../../shared/training-action-modal/training-action-modal.component';
import {ChartPanelSkeletonComponent} from '../../../shared/skeleton/chart-panel-skeleton/chart-panel-skeleton.component';
import {ActionContext} from '../../../../services/ai-action.service';
import {BehaviorSubject, map, Observable} from 'rxjs';

type TabId = 'feed' | 'sessions' | 'members' | 'stats' | 'leaderboard' | 'race-goals' | 'chat' | 'gazette' | 'tests';

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
    ClubChatTabComponent,
    ClubGazetteTabComponent,
    ClubTestsTabComponent,
    TrainingActionModalComponent,
    ChartPanelSkeletonComponent,
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
  private clubTestService = inject(ClubTestService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  selectedClub$ = this.clubService.selectedClub$;
  currentUserId: string | null = null;

  activeTab: TabId = 'sessions';
  chatView: 'list' | 'chat' = 'list';
  loadedTabs = new Set<TabId>();

  clubId = '';
  showAiModal = false;
  aiContext: ActionContext = {};
  aiSessionInfo: { scheduledAt?: string; sport?: string; clubGroupName?: string } | null = null;
  private aiSessionDate: string | undefined;
  aiExistingLinkedTrainings: GroupLinkedTraining[] = [];
  aiSessionGroupId?: string;
  private clubGroups: ClubGroup[] = [];
  private destroyRef = inject(DestroyRef);

  copiedClubCodeId: string | null = null;
  readonly isJoiningClub$ = new BehaviorSubject(false);
  readonly isLeavingClub$ = new BehaviorSubject(false);
  clubInviteCode$: Observable<ClubInviteCode | null> = this.clubService.inviteCodes$.pipe(
    map((codes) => codes.find((c) => c.active && !c.clubGroupId) ?? null),
  );

  readonly tabs: Array<{ id: TabId; label: string; shortLabel: string }> = [
    { id: 'feed', label: 'CLUB_DETAIL.TAB_FEED', shortLabel: 'CLUB_DETAIL.TAB_FEED_SHORT' },
    { id: 'sessions', label: 'CLUB_DETAIL.TAB_SESSIONS', shortLabel: 'CLUB_DETAIL.TAB_SESSIONS_SHORT' },
    { id: 'members', label: 'CLUB_DETAIL.TAB_MEMBERS', shortLabel: 'CLUB_DETAIL.TAB_MEMBERS_SHORT' },
    { id: 'stats', label: 'CLUB_DETAIL.TAB_STATS', shortLabel: 'CLUB_DETAIL.TAB_STATS_SHORT' },
    { id: 'race-goals', label: 'CLUB_DETAIL.TAB_RACE_GOALS', shortLabel: 'CLUB_DETAIL.TAB_RACE_GOALS_SHORT' },
    { id: 'tests', label: 'CLUB_DETAIL.TAB_TESTS', shortLabel: 'CLUB_DETAIL.TAB_TESTS_SHORT' },
    { id: 'gazette', label: 'CLUB_DETAIL.TAB_GAZETTE', shortLabel: 'CLUB_DETAIL.TAB_GAZETTE_SHORT' },
    { id: 'chat', label: 'CLUB_DETAIL.TAB_CHAT', shortLabel: 'CLUB_DETAIL.TAB_CHAT_SHORT' },
  ];

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });

    let prevId: string | null = null;
    this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params['id'] as string;
      if (id !== prevId) {
        prevId = id;
        this.clubId = id;
        this.loadedTabs.clear();
        this.chatView = 'list';
        this.clubService.resetDetail();
        this.clubSessionService.resetDetail();
        this.clubFeedService.resetDetail();
        this.clubService.loadClubDetail(this.clubId);
      }
      this.applyTab(this.parseTab(params['tab']));
    });

    // Load invite codes early for header display (gated by role in template)
    this.selectedClub$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((club) => {
      if (club && this.canManageInvites(club)) {
        this.clubService.loadInviteCodes(club.id);
      }
    });

    this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((groups) => {
      this.clubGroups = groups;
    });
  }

  ngOnDestroy(): void {
    this.clubService.resetDetail();
    this.clubSessionService.resetDetail();
    this.clubFeedService.resetDetail();
    this.clubTestService.resetDetail();
  }

  selectTab(tab: TabId): void {
    if (tab === this.activeTab) return;
    this.router.navigate(['/clubs', this.clubId, tab]);
  }

  private parseTab(raw: string | undefined): TabId {
    const valid = this.tabs.map((t) => t.id) as readonly string[];
    return raw && valid.includes(raw) ? (raw as TabId) : 'sessions';
  }

  private applyTab(tab: TabId): void {
    this.activeTab = tab;
    if (tab !== 'chat') this.chatView = 'list';
    if (this.loadedTabs.has(tab)) {
      this.cdr.markForCheck();
      return;
    }
    this.loadedTabs.add(tab);

    switch (tab) {
      case 'feed':
        break;
      case 'sessions':
        this.clubSessionService.loadRecurringTemplates(this.clubId);
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
        break;
      case 'gazette':
        break;
      case 'tests':
        this.clubTestService.loadTests(this.clubId);
        this.clubTestService.loadPresets(this.clubId);
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

  onChatViewChange(view: 'list' | 'chat'): void {
    this.chatView = view;
    this.cdr.markForCheck();
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
    return canManageClub(club?.currentMemberRole);
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
