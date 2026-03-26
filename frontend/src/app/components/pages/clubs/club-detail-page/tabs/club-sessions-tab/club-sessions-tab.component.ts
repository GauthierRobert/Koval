import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
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
  getEffectiveLinkedTrainings,
} from '../../../../../../services/club.service';
import {AuthService} from '../../../../../../services/auth.service';
import {TrainingService} from '../../../../../../services/training.service';
import {SportIconComponent} from '../../../../../shared/sport-icon/sport-icon.component';

type ViewMode = 'LIST' | 'CALENDAR';

@Component({
  selector: 'app-club-sessions-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, SportIconComponent],
  templateUrl: './club-sessions-tab.component.html',
  styleUrl: './club-sessions-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubSessionsTabComponent implements OnInit, AfterViewInit {
  @Input() club!: ClubDetail;
  @Output() createAiForSession = new EventEmitter<ClubTrainingSession>();
  @ViewChild('timeGridBody') timeGridBody?: ElementRef<HTMLElement>;

  private clubService = inject(ClubService);
  private authService = inject(AuthService);
  private trainingService = inject(TrainingService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  sessions$ = this.clubService.sessions$;
  currentUserId: string | null = null;

  viewMode: ViewMode = 'LIST';
  calendarWeekStart: Date = ClubSessionsTabComponent.getMonday(new Date());
  calendarDays: Date[] = [];

  // Unified form state
  isFormOpen = false;
  isRecurring = false;
  form: Record<string, any> = {};
  clubGroups: ClubGroup[] = [];

  // Edit state
  editingSession: ClubTrainingSession | null = null;
  editAllFutureMode = false;
  showRecurringEditChoice = false;
  pendingEditSession: ClubTrainingSession | null = null;

  // Cancel session state
  showCancelConfirm = false;
  cancelTargetSession: ClubTrainingSession | null = null;
  cancelReason = '';
  showCancelRecurringChoice = false;
  pendingCancelSession: ClubTrainingSession | null = null;
  cancelMode: 'single' | 'all' = 'single';

  coachMembers: ClubMember[] = [];
  expandedSessionId: string | null = null;
  private allMembers: ClubMember[] = [];

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  readonly daysOfWeek = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  // Time-grid constants
  readonly HOUR_START = 6;
  readonly HOUR_END = 22;
  readonly HOUR_HEIGHT_PX = 120;
  readonly hours = Array.from({ length: 16 }, (_, i) => i + 6);

  private scrolledToCurrentHour = false;
  private allSessions: ClubTrainingSession[] = [];
  sessionsAboveCount = 0;
  sessionsBelowCount = 0;

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.sessions$.subscribe((sessions) => {
      this.allSessions = sessions;
      this.clearOverlapCache();
    });
    this.buildCalendarDays();
    if (this.club) {
      this.clubService.loadRecurringTemplates(this.club.id); // keep for form
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

  ngAfterViewInit(): void {
    this.scrollToCurrentHour();
  }

  get canCreate(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  get isCoach(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'COACH';
  }

  // --- List view helpers ---

  getListDaysWithSessions(sessions: ClubTrainingSession[]): Date[] {
    return this.calendarDays.filter((day) => this.getSessionsForDay(sessions, day).length > 0);
  }

  getGroupName(groupId: string | undefined): string {
    if (!groupId) return '';
    const group = this.clubGroups.find((g) => g.id === groupId);
    return group?.name ?? '';
  }

  // --- View toggle ---

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
    if (mode === 'CALENDAR') {
      this.scrolledToCurrentHour = false;
      setTimeout(() => this.scrollToCurrentHour(), 50);
    }
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
    this.scrolledToCurrentHour = false;
    setTimeout(() => this.scrollToCurrentHour(), 50);
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
    this.clubService.loadSessionsForRange(this.club.id, from, to);
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

  // --- Time-grid helpers ---

  getSessionTopPx(session: ClubTrainingSession): number {
    if (!session.scheduledAt) return 0;
    const d = new Date(session.scheduledAt);
    const hours = d.getHours();
    const minutes = d.getMinutes();
    return (Math.max(hours, this.HOUR_START) - this.HOUR_START) * this.HOUR_HEIGHT_PX + (hours >= this.HOUR_START ? minutes : 0);
  }

  getSessionHeightPx(session: ClubTrainingSession): number {
    const dur = session.durationMinutes ?? 60;
    return Math.max((dur / 60) * this.HOUR_HEIGHT_PX, 28);
  }

  /**
   * Computes side-by-side layout for overlapping sessions in a day.
   * Returns a map of sessionId → { col, totalCols } for CSS positioning.
   */
  private overlapCache = new Map<string, Map<string, { col: number; totalCols: number }>>();

  getSessionLayout(sessions: ClubTrainingSession[], session: ClubTrainingSession): { left: string; width: string } {
    const dayKey = session.scheduledAt?.substring(0, 10) ?? '';
    if (!this.overlapCache.has(dayKey)) {
      this.overlapCache.set(dayKey, this.computeOverlapLayout(sessions));
    }
    const layout = this.overlapCache.get(dayKey)!.get(session.id);
    if (!layout || layout.totalCols <= 1) {
      return { left: '2px', width: 'calc(100% - 4px)' };
    }
    const colWidth = 100 / layout.totalCols;
    return {
      left: `calc(${layout.col * colWidth}% + 1px)`,
      width: `calc(${colWidth}% - 2px)`,
    };
  }

  private computeOverlapLayout(sessions: ClubTrainingSession[]): Map<string, { col: number; totalCols: number }> {
    const result = new Map<string, { col: number; totalCols: number }>();
    if (!sessions.length) return result;

    const items = sessions.map(s => ({
      id: s.id,
      top: this.getSessionTopPx(s),
      bottom: this.getSessionTopPx(s) + this.getSessionHeightPx(s),
    })).sort((a, b) => a.top - b.top || a.bottom - b.bottom);

    // Greedy column assignment
    const columns: { id: string; bottom: number }[][] = [];
    const colMap = new Map<string, number>();

    for (const item of items) {
      let placed = false;
      for (let c = 0; c < columns.length; c++) {
        const col = columns[c];
        if (col[col.length - 1].bottom <= item.top) {
          col.push(item);
          colMap.set(item.id, c);
          placed = true;
          break;
        }
      }
      if (!placed) {
        columns.push([item]);
        colMap.set(item.id, columns.length - 1);
      }
    }

    // Find overlapping groups to determine totalCols per session
    for (const item of items) {
      const overlapping = items.filter(
        other => other.id !== item.id && other.top < item.bottom && other.bottom > item.top
      );
      const cols = new Set([colMap.get(item.id)!, ...overlapping.map(o => colMap.get(o.id)!)]);
      result.set(item.id, { col: colMap.get(item.id)!, totalCols: cols.size });
    }

    return result;
  }

  clearOverlapCache(): void {
    this.overlapCache.clear();
  }

  formatHourLabel(hour: number): string {
    if (hour === 0) return '12 AM';
    if (hour < 12) return `${hour} AM`;
    if (hour === 12) return '12 PM';
    return `${hour - 12} PM`;
  }

  private scrollToCurrentHour(): void {
    if (this.scrolledToCurrentHour || !this.timeGridBody?.nativeElement) return;
    const now = new Date();
    const scrollTo = (Math.max(now.getHours() - 1, this.HOUR_START) - this.HOUR_START) * this.HOUR_HEIGHT_PX;
    this.timeGridBody.nativeElement.scrollTop = scrollTo;
    this.scrolledToCurrentHour = true;
    setTimeout(() => this.onTimeGridScroll(), 0);
  }

  getCurrentTimeTopPx(): number {
    const now = new Date();
    const hours = now.getHours();
    const minutes = now.getMinutes();
    if (hours < this.HOUR_START) return 0;
    return (hours - this.HOUR_START) * this.HOUR_HEIGHT_PX + (minutes / 60) * this.HOUR_HEIGHT_PX;
  }

  onTimeGridScroll(): void {
    const el = this.timeGridBody?.nativeElement;
    if (!el) return;

    const scrollTop = el.scrollTop;
    const viewportBottom = scrollTop + el.clientHeight;

    let above = 0;
    let below = 0;

    for (const session of this.allSessions) {
      const top = this.getSessionTopPx(session);
      const height = this.getSessionHeightPx(session);
      if (top + height < scrollTop) above++;
      else if (top > viewportBottom) below++;
    }

    if (this.sessionsAboveCount !== above || this.sessionsBelowCount !== below) {
      this.sessionsAboveCount = above;
      this.sessionsBelowCount = below;
      this.cdr.markForCheck();
    }
  }

  // --- Unified session form ---

  openForm(): void {
    this.editingSession = null;
    this.editAllFutureMode = false;
    this.form = { sport: 'CYCLING', title: '', clubGroupId: '', openToAll: false, openToAllDelayValue: 2, openToAllDelayUnit: 'DAYS' };
    this.isRecurring = false;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
    this.editingSession = null;
    this.editAllFutureMode = false;
  }

  openEditForm(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
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
    this.editingSession = session;
    this.editAllFutureMode = allFuture;
    this.isRecurring = false;
    this.form = {
      title: session.title,
      sport: session.sport || 'CYCLING',
      scheduledAt: session.scheduledAt ? this.toDatetimeLocal(session.scheduledAt) : '',
      location: session.location || '',
      description: session.description || '',
      maxParticipants: session.maxParticipants || '',
      durationMinutes: session.durationMinutes || '',
      clubGroupId: session.clubGroupId || '',
      responsibleCoachId: session.responsibleCoachId || '',
      openToAll: session.openToAll || false,
      openToAllDelayValue: session.openToAllDelayValue || 2,
      openToAllDelayUnit: session.openToAllDelayUnit || 'DAYS',
    };
    this.isFormOpen = true;
    this.cdr.markForCheck();
  }

  private toDatetimeLocal(isoStr: string): string {
    const d = new Date(isoStr);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  get isFormValid(): boolean {
    if (!this.form['title'] || !this.form['sport']) return false;
    if (this.isRecurring && (!this.form['dayOfWeek'] || !this.form['timeOfDay'])) return false;
    return true;
  }

  save(): void {
    if (!this.isFormValid) return;

    if (this.editingSession) {
      if (this.editAllFutureMode && this.editingSession.recurringTemplateId) {
        const data: CreateRecurringSessionData = {
          title: this.form['title'],
          sport: this.form['sport'],
          dayOfWeek: undefined as any,
          timeOfDay: this.form['scheduledAt'] ? new Date(this.form['scheduledAt']).toTimeString().slice(0, 5) : undefined as any,
          location: this.form['location'] || undefined,
          description: this.form['description'] || undefined,
          maxParticipants: this.form['maxParticipants'] || undefined,
          clubGroupId: this.form['clubGroupId'] || undefined,
          responsibleCoachId: this.form['responsibleCoachId'] || undefined,
          openToAll: this.form['clubGroupId'] ? this.form['openToAll'] : undefined,
          openToAllDelayValue: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayValue'] : undefined,
          openToAllDelayUnit: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayUnit'] : undefined,
        };
        this.clubService.updateRecurringTemplateWithInstances(this.club.id, this.editingSession.recurringTemplateId, data).subscribe({
          next: () => {
            this.isFormOpen = false;
            this.editingSession = null;
            this.editAllFutureMode = false;
            this.loadCalendarSessions();
            this.cdr.markForCheck();
          },
          error: () => {},
        });
      } else {
        const data: CreateSessionData = {
          title: this.form['title'],
          sport: this.form['sport'],
          scheduledAt: this.form['scheduledAt'] || undefined,
          location: this.form['location'] || undefined,
          description: this.form['description'] || undefined,
          maxParticipants: this.form['maxParticipants'] || undefined,
          durationMinutes: this.form['durationMinutes'] || undefined,
          clubGroupId: this.form['clubGroupId'] || undefined,
          responsibleCoachId: this.form['responsibleCoachId'] || undefined,
          openToAll: this.form['clubGroupId'] ? this.form['openToAll'] : undefined,
          openToAllDelayValue: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayValue'] : undefined,
          openToAllDelayUnit: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayUnit'] : undefined,
        };
        this.clubService.updateSession(this.club.id, this.editingSession.id, data).subscribe({
          next: () => {
            this.isFormOpen = false;
            this.editingSession = null;
            this.editAllFutureMode = false;
            this.loadCalendarSessions();
            this.cdr.markForCheck();
          },
          error: () => {},
        });
      }
      return;
    }

    if (this.isRecurring) {
      const data: CreateRecurringSessionData = {
        title: this.form['title'],
        sport: this.form['sport'],
        dayOfWeek: this.form['dayOfWeek'],
        timeOfDay: this.form['timeOfDay'],
        location: this.form['location'] || undefined,
        description: this.form['description'] || undefined,
        maxParticipants: this.form['maxParticipants'] || undefined,
        clubGroupId: this.form['clubGroupId'] || undefined,
        responsibleCoachId: this.form['responsibleCoachId'] || undefined,
        openToAll: this.form['clubGroupId'] ? this.form['openToAll'] : undefined,
        openToAllDelayValue: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayValue'] : undefined,
        openToAllDelayUnit: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayUnit'] : undefined,
      };
      this.clubService.createRecurringTemplate(this.club.id, data).subscribe({
        next: () => {
          this.isFormOpen = false;
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    } else {
      const data: CreateSessionData = {
        title: this.form['title'],
        sport: this.form['sport'],
        scheduledAt: this.form['scheduledAt'] || undefined,
        location: this.form['location'] || undefined,
        description: this.form['description'] || undefined,
        maxParticipants: this.form['maxParticipants'] || undefined,
        durationMinutes: this.form['durationMinutes'] || undefined,
        clubGroupId: this.form['clubGroupId'] || undefined,
        responsibleCoachId: this.form['responsibleCoachId'] || undefined,
        openToAll: this.form['clubGroupId'] ? this.form['openToAll'] : undefined,
        openToAllDelayValue: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayValue'] : undefined,
        openToAllDelayUnit: this.form['clubGroupId'] && this.form['openToAll'] ? this.form['openToAllDelayUnit'] : undefined,
      };
      this.clubService.createSession(this.club.id, data).subscribe({
        next: () => {
          this.isFormOpen = false;
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  // --- Join / Cancel ---

  joinSession(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    this.clubService.joinSession(this.club.id, session.id).subscribe({ error: () => {} });
  }

  cancelSession(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    this.clubService.cancelSession(this.club.id, session.id).subscribe({ error: () => {} });
  }

  // --- AI Create for session ---

  onAiCreateForSession(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    this.createAiForSession.emit(session);
  }

  navigateToTraining(trainingId: string, event: Event): void {
    event.stopPropagation();
    this.trainingService.getTrainingById(trainingId).subscribe((training) => {
      this.trainingService.selectTraining(training);
      this.router.navigate(['/trainings']);
    });
  }

  // --- Cancel entire session ---

  openCancelSessionModal(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    if (session.recurringTemplateId) {
      this.pendingCancelSession = session;
      this.showCancelRecurringChoice = true;
      this.cdr.markForCheck();
    } else {
      this.cancelTargetSession = session;
      this.cancelReason = '';
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
    this.cancelReason = '';
    this.cancelMode = 'single';
    this.showCancelConfirm = true;
    this.cdr.markForCheck();
  }

  cancelAllFuture(): void {
    if (!this.pendingCancelSession) return;
    this.showCancelRecurringChoice = false;
    this.cancelTargetSession = this.pendingCancelSession;
    this.pendingCancelSession = null;
    this.cancelReason = '';
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
    this.cancelReason = '';
    this.cancelMode = 'single';
  }

  confirmCancelSession(): void {
    if (!this.cancelTargetSession) return;
    if (this.cancelMode === 'all' && this.cancelTargetSession.recurringTemplateId) {
      this.clubService
        .cancelRecurringSessions(this.club.id, this.cancelTargetSession.recurringTemplateId, this.cancelReason || undefined)
        .subscribe({
          next: () => {
            this.closeCancelSessionModal();
            this.loadCalendarSessions();
            this.cdr.markForCheck();
          },
          error: () => {},
        });
    } else {
      this.clubService.cancelEntireSession(this.club.id, this.cancelTargetSession.id, this.cancelReason || undefined).subscribe({
        next: () => {
          this.closeCancelSessionModal();
          this.loadCalendarSessions();
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  isCancelled(session: ClubTrainingSession): boolean {
    return !!session.cancelled;
  }

  getOpenToAllLabel(session: ClubTrainingSession): string {
    if (!session.openToAll || !session.scheduledAt) return '';
    const delay = session.openToAllDelayValue ?? 2;
    const unit = session.openToAllDelayUnit ?? 'DAYS';
    const scheduledMs = new Date(session.scheduledAt).getTime();
    const offsetMs = unit === 'HOURS' ? delay * 3600_000 : delay * 86400_000;
    const openFromMs = scheduledMs - offsetMs;
    const nowMs = Date.now();
    if (nowMs >= openFromMs) return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_OPENED');
    const remainMs = openFromMs - nowMs;
    const remainH = Math.ceil(remainMs / 3600_000);
    if (remainH <= 48) return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_IN_HOURS', { remainH });
    const remainD = Math.ceil(remainMs / 86400_000);
    return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_IN_DAYS', { remainD });
  }

  // --- Status helpers ---

  isParticipant(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.participantIds.includes(this.currentUserId);
  }

  isFull(session: ClubTrainingSession): boolean {
    return session.maxParticipants != null && session.participantIds.length >= session.maxParticipants;
  }

  isOnWaitingList(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && !!session.waitingList?.some((e) => e.userId === this.currentUserId);
  }

  getWaitingListPosition(session: ClubTrainingSession): number {
    if (!this.currentUserId || !session.waitingList) return 0;
    const idx = session.waitingList.findIndex((e) => e.userId === this.currentUserId);
    return idx >= 0 ? idx + 1 : 0;
  }

  getCapacityText(session: ClubTrainingSession): string {
    if (session.maxParticipants == null)
      return this.translate.instant('CLUB_SESSIONS.CAPACITY_PARTICIPANTS', { count: session.participantIds.length });
    return `${session.participantIds.length}/${session.maxParticipants}`;
  }

  formatDateTime(dateStr: string | undefined): string {
    if (!dateStr) return '\u2014';
    return new Date(dateStr).toLocaleString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  formatTime(dateStr: string | undefined): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  }

  truncate(text: string | undefined, maxLen: number): string {
    if (!text) return '';
    return text.length > maxLen ? text.substring(0, maxLen) + '...' : text;
  }

  toggleSessionDetail(session: ClubTrainingSession): void {
    this.expandedSessionId = this.expandedSessionId === session.id ? null : session.id;
  }

  getParticipantNames(session: ClubTrainingSession): { name: string; initial: string }[] {
    return session.participantIds.map((id) => {
      const member = this.allMembers.find((m) => m.userId === id);
      const name = member?.displayName || id.substring(0, 8);
      return { name, initial: name.charAt(0).toUpperCase() };
    });
  }

  getWaitingListNames(session: ClubTrainingSession): { name: string; initial: string; position: number }[] {
    return (session.waitingList || []).map((entry, i) => {
      const member = this.allMembers.find((m) => m.userId === entry.userId);
      const name = member?.displayName || entry.userId.substring(0, 8);
      return { name, initial: name.charAt(0).toUpperCase(), position: i + 1 };
    });
  }

  // --- Multi-training helpers ---

  getEffectiveLinkedTrainings(session: ClubTrainingSession): GroupLinkedTraining[] {
    return getEffectiveLinkedTrainings(session);
  }

  hasAnyLinkedTraining(session: ClubTrainingSession): boolean {
    return this.getEffectiveLinkedTrainings(session).length > 0;
  }

  getUserGroupIds(): Set<string> {
    if (!this.currentUserId) return new Set();
    return new Set(
      this.clubGroups
        .filter((g) => g.memberIds.includes(this.currentUserId!))
        .map((g) => g.id),
    );
  }

  unlinkTraining(session: ClubTrainingSession, glt: GroupLinkedTraining, event: Event): void {
    event.stopPropagation();
    this.clubService.unlinkTrainingFromSession(this.club.id, session.id, glt.clubGroupId || undefined).subscribe({
      next: () => {
        this.loadCalendarSessions();
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  canLinkMoreTrainings(session: ClubTrainingSession): boolean {
    const effective = this.getEffectiveLinkedTrainings(session);
    // Club-level linked → no more
    if (effective.some(glt => !glt.clubGroupId)) return false;
    // No groups exist → can link only if nothing linked yet
    if (this.clubGroups.length === 0) return effective.length === 0;
    // All groups linked
    const linkedGroupIds = new Set(effective.filter(g => g.clubGroupId).map(g => g.clubGroupId));
    return this.clubGroups.some(g => !linkedGroupIds.has(g.id));
  }

  isInUserGroup(glt: GroupLinkedTraining): boolean {
    if (!glt.clubGroupId) return true; // club-level = visible to all
    return this.getUserGroupIds().has(glt.clubGroupId);
  }

  getUserLinkedTraining(session: ClubTrainingSession): GroupLinkedTraining | null {
    const effective = this.getEffectiveLinkedTrainings(session);
    if (effective.length === 0) return null;
    const userGroups = this.getUserGroupIds();
    // Match user's group first
    const match = effective.find((glt) => glt.clubGroupId && userGroups.has(glt.clubGroupId));
    if (match) return match;
    // Fall back to club-level
    const clubLevel = effective.find((glt) => !glt.clubGroupId);
    if (clubLevel) return clubLevel;
    // Last resort
    return effective[0];
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
