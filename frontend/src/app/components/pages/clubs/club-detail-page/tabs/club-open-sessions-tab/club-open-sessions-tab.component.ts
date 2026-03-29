import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ClubDetail, ClubService, ClubTrainingSession, CreateSessionData} from '../../../../../../services/club.service';
import {AuthService} from '../../../../../../services/auth.service';
import {SportIconComponent} from '../../../../../shared/sport-icon/sport-icon.component';
import {MeetingPointPickerComponent} from '../../../../../shared/meeting-point-picker/meeting-point-picker.component';

@Component({
  selector: 'app-club-open-sessions-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent, MeetingPointPickerComponent],
  templateUrl: './club-open-sessions-tab.component.html',
  styleUrl: './club-open-sessions-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubOpenSessionsTabComponent implements OnInit {
  @Input() club!: ClubDetail;

  private clubService = inject(ClubService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private translate = inject(TranslateService);

  openSessions$ = this.clubService.openSessions$;
  currentUserId: string | null = null;

  calendarWeekStart: Date = ClubOpenSessionsTabComponent.getMonday(new Date());
  calendarDays: Date[] = [];

  isFormOpen = false;
  editingSession: ClubTrainingSession | null = null;
  form: Record<string, any> = {};
  gpxFile: File | null = null;

  get canCreate(): boolean {
    return this.club?.currentMembershipStatus === 'ACTIVE';
  }

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
    this.buildCalendarDays();
    this.loadOpenSessions();
  }

  // --- Week navigation ---

  prevWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() - 7 * 86400000);
    this.buildCalendarDays();
    this.loadOpenSessions();
  }

  nextWeek(): void {
    this.calendarWeekStart = new Date(this.calendarWeekStart.getTime() + 7 * 86400000);
    this.buildCalendarDays();
    this.loadOpenSessions();
  }

  private buildCalendarDays(): void {
    this.calendarDays = [];
    for (let i = 0; i < 7; i++) {
      this.calendarDays.push(new Date(this.calendarWeekStart.getTime() + i * 86400000));
    }
  }

  loadOpenSessions(): void {
    const from = this.calendarWeekStart.toISOString();
    const to = new Date(this.calendarWeekStart.getTime() + 7 * 86400000).toISOString();
    this.clubService.loadOpenSessions(this.club.id, from, to);
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
      ? this.clubService.updateSession(this.club.id, this.editingSession.id, data)
      : this.clubService.createSession(this.club.id, data);

    save$.subscribe({
      next: (session) => {
        if (this.gpxFile) {
          this.clubService.uploadSessionGpx(this.club.id, session.id, this.gpxFile).subscribe({
            next: () => this.afterSave(),
            error: () => this.afterSave(),
          });
        } else {
          this.afterSave();
        }
      },
    });
  }

  private afterSave(): void {
    this.closeForm();
    this.loadOpenSessions();
  }

  // --- Actions ---

  joinSession(session: ClubTrainingSession): void {
    this.clubService.joinSession(this.club.id, session.id).subscribe({
      next: () => this.loadOpenSessions(),
    });
  }

  leaveSession(session: ClubTrainingSession): void {
    this.clubService.cancelSession(this.club.id, session.id).subscribe({
      next: () => this.loadOpenSessions(),
    });
  }

  isJoined(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.participantIds.includes(this.currentUserId);
  }

  isCreator(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.createdBy === this.currentUserId;
  }

  removeGpx(): void {
    if (this.editingSession) {
      this.clubService.deleteSessionGpx(this.club.id, this.editingSession.id).subscribe({
        next: () => this.loadOpenSessions(),
      });
    }
  }

  // --- Route preview SVG ---

  getRouteViewBox(session: ClubTrainingSession): string {
    const coords = session.routeCoordinates;
    if (!coords?.length) return '0 0 100 100';
    const lats = coords.map((c) => c.lat);
    const lons = coords.map((c) => c.lon);
    const minLat = Math.min(...lats), maxLat = Math.max(...lats);
    const minLon = Math.min(...lons), maxLon = Math.max(...lons);
    const padX = (maxLon - minLon) * 0.1 || 0.001;
    const padY = (maxLat - minLat) * 0.1 || 0.001;
    return `${minLon - padX} ${minLat - padY} ${maxLon - minLon + 2 * padX} ${maxLat - minLat + 2 * padY}`;
  }

  getRoutePoints(session: ClubTrainingSession): string {
    const coords = session.routeCoordinates;
    if (!coords?.length) return '';
    const lats = coords.map((c) => c.lat);
    const maxLat = Math.max(...lats);
    const minLat = Math.min(...lats);
    return coords.map((c) => `${c.lon},${maxLat + minLat - c.lat}`).join(' ');
  }

  // --- Helpers ---

  weekLabel(): string {
    const end = new Date(this.calendarWeekStart.getTime() + 6 * 86400000);
    const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${this.calendarWeekStart.toLocaleDateString(undefined, opts)} – ${end.toLocaleDateString(undefined, opts)}`;
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
