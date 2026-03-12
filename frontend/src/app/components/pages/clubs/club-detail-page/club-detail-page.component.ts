import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ClubDetail, ClubService } from '../../../../services/club.service';
import { AuthService } from '../../../../services/auth.service';
import { ClubFeedTabComponent } from './tabs/club-feed-tab/club-feed-tab.component';
import { ClubSessionsTabComponent } from './tabs/club-sessions-tab/club-sessions-tab.component';
import { ClubMembersTabComponent } from './tabs/club-members-tab/club-members-tab.component';
import { ClubStatsTabComponent } from './tabs/club-stats-tab/club-stats-tab.component';
import { ClubLeaderboardTabComponent } from './tabs/club-leaderboard-tab/club-leaderboard-tab.component';
import { ClubRaceGoalsTabComponent } from './tabs/club-race-goals-tab/club-race-goals-tab.component';
import { CreateWithAiModalComponent } from '../../../shared/create-with-ai-modal/create-with-ai-modal.component';
import { ActionContext, ActionResult } from '../../../../services/ai-action.service';
import { ClubTrainingSession } from '../../../../services/club.service';
import { Subscription } from 'rxjs';

type TabId = 'feed' | 'sessions' | 'members' | 'stats' | 'leaderboard' | 'race-goals';

@Component({
  selector: 'app-club-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ClubFeedTabComponent,
    ClubSessionsTabComponent,
    ClubMembersTabComponent,
    ClubStatsTabComponent,
    ClubLeaderboardTabComponent,
    ClubRaceGoalsTabComponent,
    CreateWithAiModalComponent,
  ],
  templateUrl: './club-detail-page.component.html',
  styleUrl: './club-detail-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubDetailPageComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clubService = inject(ClubService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  selectedClub$ = this.clubService.selectedClub$;
  currentUserId: string | null = null;

  activeTab: TabId = 'feed';
  loadedTabs = new Set<TabId>();

  clubId = '';
  showAiModal = false;
  aiContext: ActionContext = {};
  private subs = new Subscription();

  readonly tabs: Array<{ id: TabId; label: string }> = [
    { id: 'feed', label: 'FEED' },
    { id: 'sessions', label: 'SESSIONS' },
    { id: 'members', label: 'MEMBERS' },
    { id: 'stats', label: 'STATS' },
    { id: 'leaderboard', label: 'LEADERBOARD' },
    { id: 'race-goals', label: 'RACE GOALS' },
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
        this.clubService.loadClubDetail(this.clubId);
        this.activateTab('feed');
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.clubService.resetDetail();
  }

  activateTab(tab: TabId): void {
    this.activeTab = tab;
    if (this.loadedTabs.has(tab)) return;
    this.loadedTabs.add(tab);

    switch (tab) {
      case 'feed':
        this.clubService.loadFeed(this.clubId);
        break;
      case 'sessions':
        this.clubService.loadSessions(this.clubId);
        this.clubService.loadRecurringTemplates(this.clubId);
        break;
      case 'members':
        this.clubService.loadMembers(this.clubId);
        this.clubService.loadGroups(this.clubId);
        break;
      case 'stats':
        this.clubService.loadWeeklyStats(this.clubId);
        break;
      case 'leaderboard':
        this.clubService.loadLeaderboard(this.clubId);
        break;
      case 'race-goals':
        this.clubService.loadRaceGoals(this.clubId);
        break;
    }
    this.cdr.markForCheck();
  }

  join(club: ClubDetail): void {
    this.clubService.joinClub(club.id).subscribe({ error: () => {} });
  }

  leave(club: ClubDetail): void {
    if (club.currentMemberRole === 'OWNER') return;
    this.clubService.leaveClub(club.id).subscribe({ error: () => {} });
  }

  goBack(): void {
    this.router.navigate(['/clubs']);
  }

  hasPendingBadge(tab: TabId): boolean {
    return tab === 'members';
  }

  openAiModal(club: ClubDetail): void {
    this.aiContext = { clubId: club.id };
    this.showAiModal = true;
    this.cdr.markForCheck();
  }

  openAiModalForSession(session: ClubTrainingSession): void {
    this.aiContext = { clubId: this.clubId, sessionId: session.id };
    this.showAiModal = true;
    this.cdr.markForCheck();
  }

  onAiCreated(_result: ActionResult): void {
    this.showAiModal = false;
    this.loadedTabs.delete('sessions');
    this.activateTab('sessions');
    this.cdr.markForCheck();
  }

  getMembershipLabel(status: string | undefined): string {
    if (!status) return '';
    if (status === 'ACTIVE') return 'MEMBER';
    if (status === 'PENDING') return 'PENDING';
    return status;
  }
}
