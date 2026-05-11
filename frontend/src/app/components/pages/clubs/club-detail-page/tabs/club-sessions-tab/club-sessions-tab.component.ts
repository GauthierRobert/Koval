import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  HostListener,
  inject,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {BehaviorSubject} from 'rxjs';

import {Router, RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {
  canManageClub,
  ClubDetail,
  ClubGroup,
  ClubMember,
  ClubService,
  ClubTrainingSession,
  GroupLinkedTraining,
  isClubCoach,
} from '../../../../../../services/club.service';
import {ClubSessionService} from '../../../../../../services/club-session.service';
import {AuthService} from '../../../../../../services/auth.service';
import {SPORT_BANNER_COLORS} from '../../../../../../models/plan.model';
import {SessionCardComponent} from '../../../../../shared/session-card/session-card.component';
import {SessionFormModalComponent, SessionFormSaveEvent} from './session-form-modal/session-form-modal.component';
import {
  CreateSingleSessionModalComponent,
  SingleSessionCreateEvent,
} from './create-single-session-modal/create-single-session-modal.component';
import {
  CreateRecurringTemplateModalComponent,
  RecurringTemplateCreateEvent,
} from './create-recurring-template-modal/create-recurring-template-modal.component';
import {CancelSessionDialogsComponent} from './cancel-session-dialogs/cancel-session-dialogs.component';
import {DAYS_OF_WEEK} from './session-form-mapper';
import {
  buildWeekDays,
  formatDayHeader,
  formatWeekRange,
  getMonday,
  getSessionsForDay,
  isSameDay,
  isToday,
  shiftWeek,
} from './club-session-calendar.utils';
import {
  confirmSessionCancellation,
  downloadSessionGpx,
  runSessionAction,
  SessionActionCallbacks,
  shareSessionGpx,
  submitCreateRecurring,
  submitCreateSingle,
  submitEditSession,
  unlinkSessionTraining,
} from './club-sessions-tab.helpers';

@Component({
  selector: 'app-club-sessions-tab',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    TranslateModule,
    SessionCardComponent,
    SessionFormModalComponent,
    CreateSingleSessionModalComponent,
    CreateRecurringTemplateModalComponent,
    CancelSessionDialogsComponent,
  ],
  templateUrl: './club-sessions-tab.component.html',
  styleUrl: './club-sessions-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubSessionsTabComponent implements OnInit {
  @Input() club!: ClubDetail;
  @Output() createAiForSession = new EventEmitter<ClubTrainingSession>();

  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  sessions$ = this.clubSessionService.sessions$;
  currentUserId: string | null = null;

  calendarWeekStart: Date = getMonday(new Date());
  calendarDays: Date[] = [];
  selectedDay: Date = new Date();

  // Modal state — three independent modals
  isEditModalOpen = false;
  isCreateSingleModalOpen = false;
  isCreateRecurringModalOpen = false;
  isCreateMenuOpen = false;
  clubGroups: ClubGroup[] = [];

  // Edit state
  editingSession: ClubTrainingSession | null = null;
  editAllFutureMode = false;

  // Edit recurring choice (this session only vs entire template)
  showEditRecurringChoice = false;
  pendingEditSession: ClubTrainingSession | null = null;

  readonly isSavingSession$ = new BehaviorSubject(false);

  // Cancel session state
  showCancelConfirm = false;
  cancelTargetSession: ClubTrainingSession | null = null;
  showCancelRecurringChoice = false;
  pendingCancelSession: ClubTrainingSession | null = null;
  cancelMode: 'single' | 'all' = 'single';

  coachMembers: ClubMember[] = [];
  allMembers: ClubMember[] = [];
  expandedSessionId: string | null = null;

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  readonly daysOfWeek = DAYS_OF_WEEK;

  private static readonly SHOW_OTHER_GROUPS_KEY = 'club-sessions-show-other-groups';
  showOtherGroupSessions = localStorage.getItem(ClubSessionsTabComponent.SHOW_OTHER_GROUPS_KEY) !== 'false';

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.buildCalendarDays();
    if (this.club) {
      this.clubSessionService.loadRecurringTemplates(this.club.id);
      this.loadCalendarSessions();
      this.clubService.loadGroups(this.club.id);
      this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((groups) => {
        this.clubGroups = groups;
        this.cdr.markForCheck();
      });
      this.clubService.members$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((members) => {
        this.coachMembers = members.filter(
          (m) => m.role === 'COACH' || m.role === 'ADMIN' || m.role === 'OWNER',
        );
        this.allMembers = members;
        this.cdr.markForCheck();
      });
    }
  }

  /** Members can propose single sessions */
  get canCreateSingle(): boolean {
    return this.club?.currentMembershipStatus === 'ACTIVE';
  }

  /** Only coaches/admins/owners can create recurring weekly templates */
  get canCreateRecurring(): boolean {
    return canManageClub(this.club?.currentMemberRole);
  }

  /** Used by session-card actions (kept for backwards compat) */
  get canCreate(): boolean {
    return this.canCreateRecurring;
  }

  get isCoach(): boolean {
    return isClubCoach(this.club?.currentMemberRole);
  }

  canEditSession(session: ClubTrainingSession): boolean {
    return (!!this.currentUserId && session.createdBy === this.currentUserId) || this.canCreateRecurring;
  }

  // --- Filters ---

  toggleShowOtherGroupSessions(): void {
    this.showOtherGroupSessions = !this.showOtherGroupSessions;
    localStorage.setItem(ClubSessionsTabComponent.SHOW_OTHER_GROUPS_KEY, String(this.showOtherGroupSessions));
    this.cdr.markForCheck();
  }

  applyFilters(sessions: ClubTrainingSession[]): ClubTrainingSession[] {
    let filtered = sessions;

    // Group filter (only narrows recurring/scheduled with a group; OPEN sessions are always visible)
    if (!this.showOtherGroupSessions) {
      const userGroupIds = this.getUserGroupIds();
      filtered = filtered.filter(
        (s) => s.category === 'OPEN' || !s.clubGroupId || userGroupIds.has(s.clubGroupId),
      );
    }

    return filtered;
  }

  // --- Day strip helpers ---

  selectDay(day: Date): void {
    this.selectedDay = day;
    this.cdr.markForCheck();
  }

  isSelectedDay(day: Date): boolean {
    return isSameDay(this.selectedDay, day);
  }

  dotColor(session: ClubTrainingSession): string {
    return SPORT_BANNER_COLORS[session.sport ?? '']?.border ?? '#ff9d00';
  }

  // --- Calendar navigation ---

  prevWeek(): void { this.setCalendarWeek(shiftWeek(this.calendarWeekStart, -1)); }
  nextWeek(): void { this.setCalendarWeek(shiftWeek(this.calendarWeekStart, 1)); }
  goToday(): void { this.setCalendarWeek(getMonday(new Date())); }

  private setCalendarWeek(weekStart: Date): void {
    this.calendarWeekStart = weekStart;
    this.buildCalendarDays();
    this.loadCalendarSessions();
  }

  private buildCalendarDays(): void {
    this.calendarDays = buildWeekDays(this.calendarWeekStart);
    const today = this.calendarDays.find(isToday);
    this.selectedDay = today ?? this.calendarDays[0];
  }

  private loadCalendarSessions(): void {
    const from = this.calendarWeekStart.toISOString();
    const to = shiftWeek(this.calendarWeekStart, 1).toISOString();
    this.clubSessionService.loadSessionsForRange(this.club.id, from, to);
  }

  getSessionsForDay = getSessionsForDay;
  formatDayHeader = formatDayHeader;
  isToday = isToday;

  formatWeekRange(): string {
    return formatWeekRange(this.calendarWeekStart);
  }

  // --- Create dropdown ---

  toggleCreateMenu(): void {
    this.isCreateMenuOpen = !this.isCreateMenuOpen;
  }

  closeCreateMenu(): void {
    this.isCreateMenuOpen = false;
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.isCreateMenuOpen) {
      this.isCreateMenuOpen = false;
      this.cdr.markForCheck();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isCreateMenuOpen) {
      this.isCreateMenuOpen = false;
      this.cdr.markForCheck();
    }
  }

  startCreate(mode: 'single' | 'recurring'): void {
    if (mode === 'recurring' && !this.canCreateRecurring) return;
    if (mode === 'single' && !this.canCreateSingle) return;
    this.isCreateMenuOpen = false;
    this.editingSession = null;
    this.editAllFutureMode = false;
    if (mode === 'single') {
      this.isCreateSingleModalOpen = true;
    } else {
      this.isCreateRecurringModalOpen = true;
    }
  }

  closeCreateSingle(): void {
    this.isCreateSingleModalOpen = false;
  }

  closeCreateRecurring(): void {
    this.isCreateRecurringModalOpen = false;
  }

  closeEditForm(): void {
    this.isEditModalOpen = false;
    this.editingSession = null;
    this.editAllFutureMode = false;
  }

  openEditForm(session: ClubTrainingSession): void {
    if (session.recurringTemplateId) {
      this.pendingEditSession = session;
      this.showEditRecurringChoice = true;
      this.cdr.markForCheck();
      return;
    }
    this.startEditing(session, false);
  }

  editThisSessionOnly(): void { this.resolvePendingEdit(false); }
  editEntireTemplate(): void { this.resolvePendingEdit(true); }

  private resolvePendingEdit(allFuture: boolean): void {
    if (!this.pendingEditSession) return;
    const session = this.pendingEditSession;
    this.pendingEditSession = null;
    this.showEditRecurringChoice = false;
    this.startEditing(session, allFuture);
  }

  closeEditRecurringChoice(): void {
    this.showEditRecurringChoice = false;
    this.pendingEditSession = null;
    this.cdr.markForCheck();
  }

  private startEditing(session: ClubTrainingSession, allFuture: boolean): void {
    this.expandedSessionId = null;
    this.editingSession = session;
    this.editAllFutureMode = allFuture;
    this.isEditModalOpen = true;
    this.cdr.markForCheck();
  }

  onEditFormSaved(event: SessionFormSaveEvent): void {
    submitEditSession(this.clubSessionService, this.club.id, event, this.saveCallbacks(() => this.finishEdit()));
  }

  onCreateSingleSaved(event: SingleSessionCreateEvent): void {
    submitCreateSingle(
      this.clubSessionService,
      this.club.id,
      event,
      this.canCreateRecurring,
      this.saveCallbacks(() => this.finishCreateSingle()),
    );
  }

  onCreateRecurringSaved(event: RecurringTemplateCreateEvent): void {
    submitCreateRecurring(
      this.clubSessionService,
      this.club.id,
      event,
      this.saveCallbacks(() => this.finishCreateRecurring()),
    );
  }

  private saveCallbacks(onDone: () => void) {
    return {
      setSaving: (saving: boolean) => this.isSavingSession$.next(saving),
      onDone,
      reload: () => this.loadCalendarSessions(),
    };
  }

  private finishEdit(): void {
    this.editingSession = null;
    this.editAllFutureMode = false;
    this.afterModalSave(() => (this.isEditModalOpen = false));
  }

  private finishCreateSingle(): void {
    this.afterModalSave(() => (this.isCreateSingleModalOpen = false));
  }

  private finishCreateRecurring(): void {
    this.afterModalSave(() => (this.isCreateRecurringModalOpen = false));
  }

  private afterModalSave(closeModal: () => void): void {
    this.isSavingSession$.next(false);
    closeModal();
    this.loadCalendarSessions();
    this.cdr.markForCheck();
  }

  // --- GPX actions ---

  downloadGpx(session: ClubTrainingSession): void {
    downloadSessionGpx(this.clubSessionService, this.club.id, session);
  }

  shareGpx(session: ClubTrainingSession): Promise<void> {
    return shareSessionGpx(this.clubSessionService, this.club.id, session);
  }

  // --- Session actions ---

  joinSession(session: ClubTrainingSession): void {
    runSessionAction(this.clubSessionService.joinSession(this.club.id, session.id), this.actionCallbacks);
  }

  cancelParticipation(session: ClubTrainingSession): void {
    runSessionAction(this.clubSessionService.cancelSession(this.club.id, session.id), this.actionCallbacks);
  }

  duplicateSession(session: ClubTrainingSession): void {
    runSessionAction(
      this.clubSessionService.duplicateSession(this.club.id, session.id),
      this.actionCallbacks,
      'Failed to duplicate session',
    );
  }

  onAiCreateForSession(session: ClubTrainingSession): void {
    this.createAiForSession.emit(session);
  }

  navigateToTraining(trainingId: string): void {
    this.router.navigate(['/trainings', trainingId]);
  }

  unlinkTraining(session: ClubTrainingSession, glt: GroupLinkedTraining): void {
    unlinkSessionTraining(this.clubSessionService, this.club.id, session, glt, this.actionCallbacks);
  }

  private get actionCallbacks(): SessionActionCallbacks {
    return {
      reload: () => this.loadCalendarSessions(),
      afterChange: () => this.cdr.markForCheck(),
    };
  }

  toggleSessionDetail(session: ClubTrainingSession): void {
    this.expandedSessionId = this.expandedSessionId === session.id ? null : session.id;
  }

  // --- Cancel session ---

  openCancelSessionModal(session: ClubTrainingSession): void {
    if (session.recurringTemplateId) {
      this.pendingCancelSession = session;
      this.showCancelRecurringChoice = true;
      this.cdr.markForCheck();
    } else {
      this.cancelTargetSession = session;
      this.cancelMode = 'single';
      this.showCancelConfirm = true;
      this.cdr.markForCheck();
    }
  }

  cancelThisOnly(): void { this.resolvePendingCancel('single'); }
  cancelAllFuture(): void { this.resolvePendingCancel('all'); }

  private resolvePendingCancel(mode: 'single' | 'all'): void {
    if (!this.pendingCancelSession) return;
    this.showCancelRecurringChoice = false;
    this.cancelTargetSession = this.pendingCancelSession;
    this.pendingCancelSession = null;
    this.cancelMode = mode;
    this.showCancelConfirm = true;
    this.cdr.markForCheck();
  }

  closeCancelRecurringChoice(): void {
    this.showCancelRecurringChoice = false;
    this.pendingCancelSession = null;
  }

  closeCancelSessionModal(): void {
    this.showCancelConfirm = false;
    this.cancelTargetSession = null;
    this.cancelMode = 'single';
  }

  confirmCancelSession(cancelReason: string): void {
    if (!this.cancelTargetSession) return;
    confirmSessionCancellation(
      this.clubSessionService,
      this.club.id,
      this.cancelTargetSession,
      this.cancelMode,
      cancelReason,
      {
        reload: () => this.loadCalendarSessions(),
        afterChange: () => this.cdr.markForCheck(),
        onClose: () => this.closeCancelSessionModal(),
      },
    );
  }

  // --- Helpers ---

  private getUserGroupIds(): Set<string> {
    if (!this.currentUserId) return new Set();
    return new Set(
      this.clubGroups
        .filter((g) => g.memberIds.includes(this.currentUserId!))
        .map((g) => g.id),
    );
  }
}
