import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  ClubDetail,
  ClubGroup,
  ClubMember,
  ClubService,
  ClubTrainingSession,
  CreateSessionData,
  GroupLinkedTraining,
} from '../../../../../../services/club.service';
import {ClubSessionService} from '../../../../../../services/club-session.service';
import {AuthService} from '../../../../../../services/auth.service';
import {TrainingService} from '../../../../../../services/training.service';
import {MeetingPointPickerComponent} from '../../../../../shared/meeting-point-picker/meeting-point-picker.component';
import {SessionCardComponent} from '../../../../../shared/session-card/session-card.component';

@Component({
  selector: 'app-club-open-sessions-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, MeetingPointPickerComponent, SessionCardComponent],
  templateUrl: './club-open-sessions-tab.component.html',
  styleUrl: './club-open-sessions-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubOpenSessionsTabComponent implements OnInit {
  @Input() club!: ClubDetail;
  @Output() createAiForSession = new EventEmitter<ClubTrainingSession>();

  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private authService = inject(AuthService);
  private trainingService = inject(TrainingService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  openSessions$ = this.clubSessionService.openSessions$;
  currentUserId: string | null = null;
  allMembers: ClubMember[] = [];
  clubGroups: ClubGroup[] = [];

  calendarWeekStart: Date = ClubOpenSessionsTabComponent.getMonday(new Date());
  calendarDays: Date[] = [];

  readonly isSavingSession$ = new BehaviorSubject(false);

  isFormOpen = false;
  editingSession: ClubTrainingSession | null = null;
  form: Record<string, any> = {};
  gpxFile: File | null = null;

  expandedSessionId: string | null = null;
  showPastSessions = false;

  // Cancel session state
  showCancelConfirm = false;
  cancelTargetSession: ClubTrainingSession | null = null;
  cancelReason = '';

  get canCreate(): boolean {
    return this.club?.currentMembershipStatus === 'ACTIVE';
  }

  get isCoach(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'COACH';
  }

  get canManage(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN' || role === 'COACH';
  }

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.clubService.members$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((members) => {
      this.allMembers = members;
      this.cdr.markForCheck();
    });
    this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((groups) => {
      this.clubGroups = groups;
      this.cdr.markForCheck();
    });
    this.buildCalendarDays();
    this.loadActivities();
  }

  // --- Week navigation ---

  prevWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() - 7 * 86400000);
    this.buildCalendarDays();
    this.loadActivities();
  }

  nextWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() + 7 * 86400000);
    this.buildCalendarDays();
    this.loadActivities();
  }

  private buildCalendarDays(): void {
    this.calendarDays = [];
    for (let i = 0; i < 7; i++) {
      this.calendarDays.push(new Date(this.calendarWeekStart.getTime() + i * 86400000));
    }
  }

  loadActivities(): void {
    const from = this.calendarWeekStart.toISOString();
    const to = new Date(this.calendarWeekStart.getTime() + 7 * 86400000).toISOString();
    this.clubSessionService.loadActivities(this.club.id, from, to);
  }

  // --- Filters ---

  toggleShowPastSessions(): void {
    this.showPastSessions = !this.showPastSessions;
    this.cdr.markForCheck();
  }

  filterActivities(sessions: ClubTrainingSession[]): ClubTrainingSession[] {
    // Show OPEN sessions + non-recurring SCHEDULED sessions (coach single sessions)
    let filtered = sessions.filter(
      (s) => s.category === 'OPEN' || (s.category === 'SCHEDULED' && !s.recurringTemplateId),
    );

    // Past sessions filter
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

  // --- Group sessions by date ---

  groupByDay(sessions: ClubTrainingSession[]): { date: Date; sessions: ClubTrainingSession[] }[] {
    const map = new Map<string, ClubTrainingSession[]>();
    for (const s of sessions) {
      const key = s.scheduledAt ? s.scheduledAt.substring(0, 10) : 'unknown';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(s);
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, sessions]) => ({ date: new Date(key), sessions }));
  }

  isToday(date: Date): boolean {
    const now = new Date();
    return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
  }

  // --- Form ---

  openForm(session?: ClubTrainingSession): void {
    this.editingSession = session ?? null;
    this.gpxFile = null;
    this.form = session
      ? {
          title: session.title,
          sport: session.sport ?? 'CYCLING',
          scheduledAt: session.scheduledAt?.substring(0, 16) ?? '',
          location: session.location ?? '',
          meetingPointLat: session.meetingPointLat,
          meetingPointLon: session.meetingPointLon,
          description: session.description ?? '',
          maxParticipants: session.maxParticipants,
          durationMinutes: session.durationMinutes,
        }
      : {
          title: '',
          sport: 'CYCLING',
          scheduledAt: '',
          location: '',
          description: '',
          maxParticipants: null,
          durationMinutes: null,
        };
    this.isFormOpen = true;
    this.cdr.markForCheck();
  }

  closeForm(): void {
    this.isFormOpen = false;
    this.editingSession = null;
    this.cdr.markForCheck();
  }

  onGpxFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.gpxFile = input.files?.[0] ?? null;
  }

  onMeetingPointSelected(point: { lat: number; lon: number } | null): void {
    this.form['meetingPointLat'] = point?.lat ?? null;
    this.form['meetingPointLon'] = point?.lon ?? null;
  }

  save(): void {
    if (!this.form['title'] || !this.form['scheduledAt']) return;

    const data: CreateSessionData = {
      category: 'OPEN',
      title: this.form['title'],
      sport: this.form['sport'],
      scheduledAt: this.form['scheduledAt'],
      location: this.form['location'] || undefined,
      meetingPointLat: this.form['meetingPointLat'],
      meetingPointLon: this.form['meetingPointLon'],
      description: this.form['description'] || undefined,
      maxParticipants: this.form['maxParticipants'] || undefined,
      durationMinutes: this.form['durationMinutes'] || undefined,
    };

    const save$ = this.editingSession
      ? this.clubSessionService.updateSession(this.club.id, this.editingSession.id, data)
      : this.clubSessionService.createSession(this.club.id, data);

    this.isSavingSession$.next(true);
    save$.subscribe({
      next: (session) => {
        if (this.gpxFile) {
          this.clubSessionService.uploadSessionGpx(this.club.id, session.id, this.gpxFile).subscribe({
            next: () => this.afterSave(),
            error: () => this.afterSave(),
          });
        } else {
          this.afterSave();
        }
      },
      error: () => this.isSavingSession$.next(false),
    });
  }

  private afterSave(): void {
    this.isSavingSession$.next(false);
    this.closeForm();
    this.loadActivities();
  }

  // --- Card actions ---

  joinSession(session: ClubTrainingSession): void {
    this.clubSessionService.joinSession(this.club.id, session.id).subscribe({
      next: () => this.loadActivities(),
    });
  }

  leaveSession(session: ClubTrainingSession): void {
    this.clubSessionService.cancelSession(this.club.id, session.id).subscribe({
      next: () => this.loadActivities(),
    });
  }

  editSession(session: ClubTrainingSession): void {
    this.openForm(session);
  }

  onLinkTraining(session: ClubTrainingSession): void {
    this.createAiForSession.emit(session);
  }

  unlinkTraining(event: { session: ClubTrainingSession; glt: GroupLinkedTraining }): void {
    this.clubSessionService.unlinkTrainingFromSession(this.club.id, event.session.id, event.glt.clubGroupId || undefined).subscribe({
      next: () => {
        this.loadActivities();
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  navigateToTraining(trainingId: string): void {
    this.trainingService.getTrainingById(trainingId).subscribe((training) => {
      this.trainingService.selectTraining(training);
      this.router.navigate(['/trainings']);
    });
  }

  toggleDetail(session: ClubTrainingSession): void {
    this.expandedSessionId = this.expandedSessionId === session.id ? null : session.id;
  }

  openCancelSessionModal(session: ClubTrainingSession): void {
    this.cancelTargetSession = session;
    this.cancelReason = '';
    this.showCancelConfirm = true;
    this.cdr.markForCheck();
  }

  closeCancelSessionModal(): void {
    this.showCancelConfirm = false;
    this.cancelTargetSession = null;
    this.cancelReason = '';
  }

  confirmCancelSession(): void {
    if (!this.cancelTargetSession) return;
    this.clubSessionService.cancelEntireSession(this.club.id, this.cancelTargetSession.id, this.cancelReason || undefined).subscribe({
      next: () => {
        this.closeCancelSessionModal();
        this.loadActivities();
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  removeGpx(): void {
    if (this.editingSession) {
      this.clubSessionService.deleteSessionGpx(this.club.id, this.editingSession.id).subscribe({
        next: () => this.loadActivities(),
      });
    }
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

  // --- Helpers ---

  weekLabel(): string {
    const end = new Date(this.calendarWeekStart.getTime() + 6 * 86400000);
    const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${this.calendarWeekStart.toLocaleDateString(undefined, opts)} – ${end.toLocaleDateString(undefined, opts)}`;
  }

  /** Check if user can edit/cancel this session (creator or coach/admin) */
  canEditSession(session: ClubTrainingSession): boolean {
    return (!!this.currentUserId && session.createdBy === this.currentUserId) || this.canManage;
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
