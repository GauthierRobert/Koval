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
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import {
  ClubDetail,
  ClubGroup,
  ClubMember,
  ClubService,
  ClubTrainingSession,
  CreateSessionData,
  CreateRecurringSessionData,
} from '../../../../../../services/club.service';
import { AuthService } from '../../../../../../services/auth.service';
import { TrainingService } from '../../../../../../services/training.service';
import { Router } from '@angular/router';

type ViewMode = 'LIST' | 'CALENDAR';

@Component({
  selector: 'app-club-sessions-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
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

  coachMembers: ClubMember[] = [];

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
    this.form = { sport: 'CYCLING', title: '', clubGroupId: '', openToAll: false, openToAllDelayValue: 2, openToAllDelayUnit: 'DAYS' };
    this.isRecurring = false;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  get isFormValid(): boolean {
    if (!this.form['title'] || !this.form['sport']) return false;
    if (this.isRecurring && (!this.form['dayOfWeek'] || !this.form['timeOfDay'])) return false;
    return true;
  }

  save(): void {
    if (!this.isFormValid) return;

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
    if (session.maxParticipants == null) return `${session.participantIds.length} participants`;
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

  private static getMonday(d: Date): Date {
    const date = new Date(d);
    const day = date.getDay();
    const diff = date.getDate() - day + (day === 0 ? -6 : 1);
    date.setDate(diff);
    date.setHours(0, 0, 0, 0);
    return date;
  }
}
