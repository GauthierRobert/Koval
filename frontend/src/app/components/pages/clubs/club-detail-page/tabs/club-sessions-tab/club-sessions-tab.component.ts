import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';

import {Router, RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  ClubDetail,
  ClubGroup,
  ClubMember,
  ClubService,
  ClubTrainingSession,
  CreateRecurringSessionData,
  CreateSessionData,
  GroupLinkedTraining,
} from '../../../../../../services/club.service';
import {ClubSessionService} from '../../../../../../services/club-session.service';
import {AuthService} from '../../../../../../services/auth.service';
import {TrainingService} from '../../../../../../services/training.service';
import {SessionCardComponent} from '../../../../../shared/session-card/session-card.component';
import {SessionFormModalComponent, SessionFormSaveEvent} from './session-form-modal/session-form-modal.component';
import {CancelSessionDialogsComponent} from './cancel-session-dialogs/cancel-session-dialogs.component';

@Component({
  selector: 'app-club-sessions-tab',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule, SessionCardComponent, SessionFormModalComponent, CancelSessionDialogsComponent],
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
  private trainingService = inject(TrainingService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  sessions$ = this.clubSessionService.sessions$;
  currentUserId: string | null = null;

  calendarWeekStart: Date = ClubSessionsTabComponent.getMonday(new Date());
  calendarDays: Date[] = [];

  // Form state
  isFormOpen = false;
  clubGroups: ClubGroup[] = [];

  // Edit state
  editingSession: ClubTrainingSession | null = null;
  editAllFutureMode = false;
  showRecurringEditChoice = false;
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
  readonly daysOfWeek = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  private static readonly SHOW_OTHER_GROUPS_KEY = 'club-sessions-show-other-groups';
  showOtherGroupSessions = localStorage.getItem(ClubSessionsTabComponent.SHOW_OTHER_GROUPS_KEY) !== 'false';
  showPastSessions = false;

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.buildCalendarDays();
    if (this.club) {
      this.clubSessionService.loadRecurringTemplates(this.club.id);
      this.loadCalendarSessions();
      this.clubService.loadGroups(this.club.id);
      this.clubService.groups$.subscribe((groups) => {
        this.clubGroups = groups;
        this.cdr.markForCheck();
      });
      this.clubService.members$.subscribe((members) => {
        this.coachMembers = members.filter(
          (m) => m.role === 'COACH' || m.role === 'ADMIN' || m.role === 'OWNER',
        );
        this.allMembers = members;
        this.cdr.markForCheck();
      });
    }
  }

  get canCreate(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  get isCoach(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'COACH';
  }

  // --- Filters ---

  toggleShowOtherGroupSessions(): void {
    this.showOtherGroupSessions = !this.showOtherGroupSessions;
    localStorage.setItem(ClubSessionsTabComponent.SHOW_OTHER_GROUPS_KEY, String(this.showOtherGroupSessions));
    this.cdr.markForCheck();
  }

  toggleShowPastSessions(): void {
    this.showPastSessions = !this.showPastSessions;
    this.cdr.markForCheck();
  }

  applyFilters(sessions: ClubTrainingSession[]): ClubTrainingSession[] {
    let filtered = sessions;

    // Only recurring sessions
    filtered = filtered.filter((s) => !!s.recurringTemplateId);

    // Group filter
    if (!this.showOtherGroupSessions) {
      const userGroupIds = this.getUserGroupIds();
      filtered = filtered.filter((s) => !s.clubGroupId || userGroupIds.has(s.clubGroupId));
    }

    // Past sessions filter (hide sessions before midnight today)
    if (!this.showPastSessions) {
      const todayMidnight = new Date();
      todayMidnight.setHours(0, 0, 0, 0);
      filtered = filtered.filter((s) => {
        if (!s.scheduledAt) return true;
        return new Date(s.scheduledAt) >= todayMidnight;
      });
    }

    return filtered;
  }

  // --- List view helpers ---

  getListDaysWithSessions(sessions: ClubTrainingSession[]): Date[] {
    return this.calendarDays.filter((day) => this.getSessionsForDay(sessions, day).length > 0);
  }

  // --- Calendar navigation ---

  prevWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() - 7 * 86400000);
    this.buildCalendarDays();
    this.loadCalendarSessions();
  }

  nextWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() + 7 * 86400000);
    this.buildCalendarDays();
    this.loadCalendarSessions();
  }

  goToday(): void {
    this.calendarWeekStart = ClubSessionsTabComponent.getMonday(new Date());
    this.buildCalendarDays();
    this.loadCalendarSessions();
  }

  private buildCalendarDays(): void {
    this.calendarDays = [];
    for (let i = 0; i < 7; i++) {
      this.calendarDays.push(new Date(this.calendarWeekStart.getTime() + i * 86400000));
    }
  }

  private loadCalendarSessions(): void {
    const from = this.calendarWeekStart.toISOString();
    const to = new Date(this.calendarWeekStart.getTime() + 7 * 86400000).toISOString();
    this.clubSessionService.loadSessionsForRange(this.club.id, from, to, 'SCHEDULED');
  }

  getSessionsForDay(sessions: ClubTrainingSession[], day: Date): ClubTrainingSession[] {
    return sessions
      .filter((s) => {
        if (!s.scheduledAt) return false;
        const d = new Date(s.scheduledAt);
        return d.getFullYear() === day.getFullYear() && d.getMonth() === day.getMonth() && d.getDate() === day.getDate();
      })
      .sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''));
  }

  formatDayHeader(day: Date): string {
    return day.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  }

  isToday(day: Date): boolean {
    const now = new Date();
    return day.getFullYear() === now.getFullYear() && day.getMonth() === now.getMonth() && day.getDate() === now.getDate();
  }

  formatWeekRange(): string {
    const end = new Date(this.calendarWeekStart.getTime() + 6 * 86400000);
    const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${this.calendarWeekStart.toLocaleDateString('en-US', opts)} - ${end.toLocaleDateString('en-US', opts)}`;
  }

  // --- Form ---

  openForm(): void {
    this.editingSession = null;
    this.editAllFutureMode = false;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
    this.editingSession = null;
    this.editAllFutureMode = false;
  }

  openEditForm(session: ClubTrainingSession): void {
    if (session.recurringTemplateId) {
      this.pendingEditSession = session;
      this.showRecurringEditChoice = true;
      this.cdr.markForCheck();
    } else {
      this.startEditing(session, false);
    }
  }

  editThisOnly(): void {
    if (!this.pendingEditSession) return;
    this.showRecurringEditChoice = false;
    this.startEditing(this.pendingEditSession, false);
    this.pendingEditSession = null;
  }

  editAllFuture(): void {
    if (!this.pendingEditSession) return;
    this.showRecurringEditChoice = false;
    this.startEditing(this.pendingEditSession, true);
    this.pendingEditSession = null;
  }

  private startEditing(session: ClubTrainingSession, allFuture: boolean): void {
    this.expandedSessionId = null;
    this.editingSession = session;
    this.editAllFutureMode = allFuture;
    this.isFormOpen = true;
    this.cdr.markForCheck();
  }

  onFormSaved(event: SessionFormSaveEvent): void {
    const form = event.form;
    this.isSavingSession$.next(true);

    // Handle removeGpx action from sub-component
    if (form['__action'] === 'removeGpx') {
      if (event.editingSession) {
        this.clubSessionService.deleteSessionGpx(this.club.id, event.editingSession.id).subscribe({
          next: () => this.loadCalendarSessions(),
        });
      }
      return;
    }

    if (event.editingSession) {
      if (event.editAllFutureMode && event.editingSession.recurringTemplateId) {
        const data: CreateRecurringSessionData = {
          category: 'SCHEDULED',
          title: form['title'],
          sport: form['sport'],
          dayOfWeek: undefined as any,
          timeOfDay: form['scheduledAt'] ? new Date(form['scheduledAt']).toTimeString().slice(0, 5) : undefined as any,
          location: form['location'] || undefined,
          meetingPointLat: form['meetingPointLat'] ?? undefined,
          meetingPointLon: form['meetingPointLon'] ?? undefined,
          description: form['description'] || undefined,
          maxParticipants: form['maxParticipants'] || undefined,
          clubGroupId: form['clubGroupId'] || undefined,
          responsibleCoachId: form['responsibleCoachId'] || undefined,
          openToAll: form['openToAll'] ?? false,
          openToAllDelayValue: form['openToAll'] ? form['openToAllDelayValue'] : undefined,
          openToAllDelayUnit: form['openToAll'] ? form['openToAllDelayUnit'] : undefined,
          endDate: form['endDate'] || undefined,
        };
        this.clubSessionService.updateRecurringTemplateWithInstances(this.club.id, event.editingSession.recurringTemplateId, data).subscribe({
          next: () => this.finishSave(),
          error: () => this.isSavingSession$.next(false),
        });
      } else {
        const data: CreateSessionData = {
          category: 'SCHEDULED',
          title: form['title'],
          sport: form['sport'],
          scheduledAt: form['scheduledAt'] || undefined,
          location: form['location'] || undefined,
          meetingPointLat: form['meetingPointLat'] ?? undefined,
          meetingPointLon: form['meetingPointLon'] ?? undefined,
          description: form['description'] || undefined,
          maxParticipants: form['maxParticipants'] || undefined,
          durationMinutes: form['durationMinutes'] || undefined,
          clubGroupId: form['clubGroupId'] || undefined,
          responsibleCoachId: form['responsibleCoachId'] || undefined,
          openToAll: form['openToAll'] ?? false,
          openToAllDelayValue: form['openToAll'] ? form['openToAllDelayValue'] : undefined,
          openToAllDelayUnit: form['openToAll'] ? form['openToAllDelayUnit'] : undefined,
        };
        const editId = event.editingSession.id;
        this.clubSessionService.updateSession(this.club.id, editId, data).subscribe({
          next: () => this.afterSaveSession(editId, event.gpxFile),
          error: () => this.isSavingSession$.next(false),
        });
      }
      return;
    }

    // Create new recurring session (always recurring)
    const data: CreateRecurringSessionData = {
      category: 'SCHEDULED',
      title: form['title'],
      sport: form['sport'],
      dayOfWeek: form['dayOfWeek'],
      timeOfDay: form['timeOfDay'],
      location: form['location'] || undefined,
      meetingPointLat: form['meetingPointLat'] ?? undefined,
      meetingPointLon: form['meetingPointLon'] ?? undefined,
      description: form['description'] || undefined,
      maxParticipants: form['maxParticipants'] || undefined,
      clubGroupId: form['clubGroupId'] || undefined,
      responsibleCoachId: form['responsibleCoachId'] || undefined,
      openToAll: form['openToAll'] ?? false,
      openToAllDelayValue: form['openToAll'] ? form['openToAllDelayValue'] : undefined,
      openToAllDelayUnit: form['openToAll'] ? form['openToAllDelayUnit'] : undefined,
      endDate: form['endDate'] || undefined,
    };
    this.clubSessionService.createRecurringTemplate(this.club.id, data).subscribe({
      next: () => this.finishSave(),
      error: () => {},
    });
  }

  private afterSaveSession(sessionId: string, gpxFile: File | null): void {
    if (gpxFile) {
      this.clubSessionService.uploadSessionGpx(this.club.id, sessionId, gpxFile).subscribe({
        next: () => this.finishSave(),
        error: () => this.finishSave(),
      });
    } else {
      this.finishSave();
    }
  }

  private finishSave(): void {
    this.isSavingSession$.next(false);
    this.isFormOpen = false;
    this.editingSession = null;
    this.editAllFutureMode = false;
    this.loadCalendarSessions();
    this.cdr.markForCheck();
  }

  // --- GPX actions ---

  downloadGpx(session: ClubTrainingSession): void {
    this.clubSessionService.downloadSessionGpx(this.club.id, session.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = session.gpxFileName ?? 'route.gpx';
        a.click();
        URL.revokeObjectURL(url);
      },
    });
  }

  async shareGpx(session: ClubTrainingSession): Promise<void> {
    if (!navigator.share) {
      this.downloadGpx(session);
      return;
    }
    this.clubSessionService.downloadSessionGpx(this.club.id, session.id).subscribe({
      next: async (blob) => {
        const file = new File([blob], session.gpxFileName ?? 'route.gpx', { type: 'application/gpx+xml' });
        try {
          await navigator.share({ title: session.title, files: [file] });
        } catch {
          // user cancelled share
        }
      },
    });
  }

  // --- Session actions ---

  joinSession(session: ClubTrainingSession): void {
    this.clubSessionService.joinSession(this.club.id, session.id).subscribe({
      next: () => this.loadCalendarSessions(),
      error: () => {},
    });
  }

  cancelParticipation(session: ClubTrainingSession): void {
    this.clubSessionService.cancelSession(this.club.id, session.id).subscribe({
      next: () => this.loadCalendarSessions(),
      error: () => {},
    });
  }

  onAiCreateForSession(session: ClubTrainingSession): void {
    this.createAiForSession.emit(session);
  }

  navigateToTraining(trainingId: string): void {
    this.trainingService.getTrainingById(trainingId).subscribe((training) => {
      this.trainingService.selectTraining(training);
      this.router.navigate(['/trainings']);
    });
  }

  unlinkTraining(session: ClubTrainingSession, glt: GroupLinkedTraining): void {
    this.clubSessionService.unlinkTrainingFromSession(this.club.id, session.id, glt.clubGroupId || undefined).subscribe({
      next: () => {
        this.loadCalendarSessions();
        this.cdr.markForCheck();
      },
      error: () => {},
    });
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

  cancelThisOnly(): void {
    if (!this.pendingCancelSession) return;
    this.showCancelRecurringChoice = false;
    this.cancelTargetSession = this.pendingCancelSession;
    this.pendingCancelSession = null;
    this.cancelMode = 'single';
    this.showCancelConfirm = true;
    this.cdr.markForCheck();
  }

  cancelAllFuture(): void {
    if (!this.pendingCancelSession) return;
    this.showCancelRecurringChoice = false;
    this.cancelTargetSession = this.pendingCancelSession;
    this.pendingCancelSession = null;
    this.cancelMode = 'all';
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
    if (this.cancelMode === 'all' && this.cancelTargetSession.recurringTemplateId) {
      this.clubSessionService
        .cancelRecurringSessions(this.club.id, this.cancelTargetSession.recurringTemplateId, cancelReason || undefined)
        .subscribe({
          next: () => {
            this.closeCancelSessionModal();
            this.loadCalendarSessions();
            this.cdr.markForCheck();
          },
          error: () => {},
        });
    } else {
      this.clubSessionService.cancelEntireSession(this.club.id, this.cancelTargetSession.id, cancelReason || undefined).subscribe({
        next: () => {
          this.closeCancelSessionModal();
          this.loadCalendarSessions();
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
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

  private static getMonday(d: Date): Date {
    const date = new Date(d);
    const day = date.getDay();
    const diff = date.getDate() - day + (day === 0 ? -6 : 1);
    date.setDate(diff);
    date.setHours(0, 0, 0, 0);
    return date;
  }
}
