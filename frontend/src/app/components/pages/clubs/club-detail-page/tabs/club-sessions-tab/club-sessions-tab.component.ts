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
  ClubService,
  ClubTrainingSession,
  CreateSessionData,
  RecurringSessionTemplate,
  CreateRecurringSessionData,
} from '../../../../../../services/club.service';
import { AuthService } from '../../../../../../services/auth.service';

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
  private cdr = inject(ChangeDetectorRef);

  sessions$ = this.clubService.sessions$;
  recurringTemplates$ = this.clubService.recurringTemplates$;
  currentUserId: string | null = null;

  viewMode: ViewMode = 'LIST';
  calendarWeekStart: Date = ClubSessionsTabComponent.getMonday(new Date());
  calendarDays: Date[] = [];

  isFormOpen = false;
  isRecurring = false;
  form: Partial<CreateSessionData> = {};
  recurringForm: Partial<CreateRecurringSessionData> = {};

  isRecurringFormOpen = false;

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];
  readonly daysOfWeek = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  // Time-grid constants
  readonly HOUR_START = 6;
  readonly HOUR_END = 22;
  readonly HOUR_HEIGHT_PX = 60;
  readonly hours = Array.from({ length: 16 }, (_, i) => i + 6);

  private scrolledToCurrentHour = false;

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.buildCalendarDays();
    if (this.club) {
      this.clubService.loadRecurringTemplates(this.club.id);
    }
  }

  ngAfterViewInit(): void {
    this.scrollToCurrentHour();
  }

  get canCreate(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  // --- View toggle ---

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
    if (mode === 'CALENDAR') {
      this.loadCalendarSessions();
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
    return sessions.filter((s) => {
      if (!s.scheduledAt) return false;
      const d = new Date(s.scheduledAt);
      return d.getFullYear() === day.getFullYear() && d.getMonth() === day.getMonth() && d.getDate() === day.getDate();
    });
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
  }

  // --- Session form ---

  openForm(): void {
    this.form = { sport: 'CYCLING' };
    this.isRecurring = false;
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  save(): void {
    if (!this.form.title) return;
    const data: CreateSessionData = {
      ...this.form,
      maxParticipants: this.form.maxParticipants || undefined,
      durationMinutes: this.form.durationMinutes || undefined,
    } as CreateSessionData;
    this.clubService.createSession(this.club.id, data).subscribe({
      next: () => {
        this.isFormOpen = false;
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  // --- Recurring form ---

  openRecurringForm(): void {
    this.recurringForm = { sport: 'CYCLING', dayOfWeek: 'TUESDAY', timeOfDay: '18:30' };
    this.isRecurringFormOpen = true;
  }

  closeRecurringForm(): void {
    this.isRecurringFormOpen = false;
  }

  saveRecurring(): void {
    if (!this.recurringForm.title || !this.recurringForm.dayOfWeek || !this.recurringForm.timeOfDay) return;
    this.clubService.createRecurringTemplate(this.club.id, this.recurringForm as CreateRecurringSessionData).subscribe({
      next: () => {
        this.isRecurringFormOpen = false;
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  deactivateTemplate(template: RecurringSessionTemplate, event: Event): void {
    event.stopPropagation();
    this.clubService.deleteRecurringTemplate(this.club.id, template.id).subscribe({ error: () => {} });
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

  formatTemplateDay(dayOfWeek: string): string {
    return dayOfWeek.charAt(0) + dayOfWeek.slice(1).toLowerCase();
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
