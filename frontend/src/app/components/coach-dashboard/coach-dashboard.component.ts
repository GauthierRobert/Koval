import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, Observable, of, map } from 'rxjs';
import { CoachService, ScheduledWorkout } from '../../services/coach.service';
import { AuthService, User } from '../../services/auth.service';
import { Tag } from '../../services/tag.service';
import { ZoneService } from '../../services/zone.service';
import { ZoneSystem } from '../../services/zone';
import { PmcDataPoint } from '../../services/metrics.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { InviteCodeModalComponent } from '../invite-code-modal/invite-code-modal.component';
import { ShareTrainingModalComponent } from '../share-training-modal/share-training-modal.component';
import { Training, TrainingService, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { PmcChartComponent } from '../pmc-chart/pmc-chart.component';

import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ScheduleModalComponent, InviteCodeModalComponent, ShareTrainingModalComponent, SportIconComponent, PmcChartComponent],
  templateUrl: './coach-dashboard.component.html',
  styleUrl: './coach-dashboard.component.css',
})
export class CoachDashboardComponent implements OnInit {
  selectedAthlete: User | null = null;
  isScheduleModalOpen = false;
  isInviteCodeModalOpen = false;
  isShareModalOpen = false;
  trainingToShare: Training | null = null;
  activeTagFilter: string | null = null;
  activeTab: 'performance' | 'physiology' | 'history' = 'performance';

  scheduleWeekStart: Date = this.getMondayOfWeek(new Date());
  scheduleWeekEnd: Date = this.getSundayOfWeek(new Date());

  private coachZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  coachZoneSystems$ = this.coachZoneSystemsSubject.asObservable();

  readonly ZONE_COLORS = ['#6366f1', '#3b82f6', '#22c55e', '#eab308', '#f97316', '#ef4444', '#7f1d1d'];
  getZoneColor(i: number): string { return this.ZONE_COLORS[i % this.ZONE_COLORS.length]; }

  private userId = '';

  // Reactive state
  private athletesSubject = new BehaviorSubject<User[]>([]);
  athletes$ = this.athletesSubject.asObservable();

  private tagsSubject = new BehaviorSubject<Tag[]>([]);
  allTags$ = this.tagsSubject.asObservable();

  private tagFilterSubject = new BehaviorSubject<string | null>(null);

  filteredAthletes$: Observable<User[]> = combineLatest([
    this.athletes$,
    this.tagFilterSubject,
  ]).pipe(
    map(([athletes, filter]) => filter ? athletes.filter(a => a.tags?.includes(filter)) : athletes)
  );

  private scheduleSubject = new BehaviorSubject<ScheduledWorkout[]>([]);
  athleteSchedule$ = this.scheduleSubject.asObservable();

  private athleteSessionsSubject = new BehaviorSubject<any[]>([]);
  athleteSessions$ = this.athleteSessionsSubject.asObservable();

  private athletePmcSubject = new BehaviorSubject<PmcDataPoint[]>([]);
  athletePmc$ = this.athletePmcSubject.asObservable();

  coachTrainings$: Observable<Training[]> = of([]);

  constructor(
    private coachService: CoachService,
    private authService: AuthService,
    private trainingService: TrainingService,
    private zoneService: ZoneService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      if (u) {
        this.userId = u.id;
        this.loadAthletes();
        this.loadTags();
        this.zoneService.getCoachZoneSystems().subscribe({
          next: (systems) => this.coachZoneSystemsSubject.next(systems),
          error: () => {}
        });
      }
    });
    this.coachTrainings$ = this.trainingService.trainings$;
  }

  private getMondayOfWeek(d: Date): Date {
    const day = d.getDay();
    const offset = day === 0 ? -6 : 1 - day;
    const m = new Date(d);
    m.setDate(d.getDate() + offset);
    m.setHours(0, 0, 0, 0);
    return m;
  }

  private getSundayOfWeek(d: Date): Date {
    const mon = this.getMondayOfWeek(d);
    const sun = new Date(mon);
    sun.setDate(mon.getDate() + 6);
    return sun;
  }

  navigateScheduleWeek(dir: -1 | 1): void {
    this.scheduleWeekStart.setDate(this.scheduleWeekStart.getDate() + dir * 7);
    this.scheduleWeekEnd.setDate(this.scheduleWeekEnd.getDate() + dir * 7);
    this.scheduleWeekStart = new Date(this.scheduleWeekStart);
    this.scheduleWeekEnd = new Date(this.scheduleWeekEnd);
    if (this.selectedAthlete) this.loadAthleteSchedule(this.selectedAthlete.id);
  }

  get scheduleWeekLabel(): string {
    const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${this.scheduleWeekStart.toLocaleDateString('en-US', opts)} – ${this.scheduleWeekEnd.toLocaleDateString('en-US', opts)}`;
  }

  loadAthletes() {
    this.coachService.getAthletes().subscribe({
      next: (data) => this.athletesSubject.next(data),
      error: (err) => console.error('Error loading athletes', err)
    });
  }

  loadTags() {
    this.coachService.getAllTags().subscribe({
      next: (tags) => this.tagsSubject.next(tags),
      error: (err) => console.error('Error loading tags', err)
    });
  }

  getTagCount(tag: string): number {
    return this.athletesSubject.value.filter(a => a.tags?.includes(tag)).length;
  }

  setTagFilter(tag: string | null) {
    this.activeTagFilter = tag;
    this.tagFilterSubject.next(tag);
  }

  toggleTagFilter(tag: string) {
    this.setTagFilter(this.activeTagFilter === tag ? null : tag);
  }

  selectAthlete(athlete: User) {
    this.scheduleWeekStart = this.getMondayOfWeek(new Date());
    this.scheduleWeekEnd = this.getSundayOfWeek(new Date());
    this.selectedAthlete = athlete;
    this.loadAthleteSchedule(athlete.id);
    this.loadAthleteSessions(athlete.id);
    this.loadAthletePmc(athlete.id);
  }

  loadAthleteSessions(athleteId: string): void {
    this.coachService.getAthleteSessions(athleteId).subscribe({
      next: (sessions: any[]) => this.athleteSessionsSubject.next(sessions),
      error: () => this.athleteSessionsSubject.next([]),
    });
  }

  loadAthletePmc(athleteId: string): void {
    const now = new Date();
    const from = new Date(now); from.setDate(from.getDate() - 30);
    const to = new Date(now); to.setDate(to.getDate() + 30);
    this.coachService.getAthletePmc(
      athleteId,
      from.toISOString().split('T')[0],
      to.toISOString().split('T')[0]
    ).subscribe({
      next: (data) => this.athletePmcSubject.next(data),
      error: () => this.athletePmcSubject.next([]),
    });
  }

  viewAthletePmc(athleteId: string): void {
    this.router.navigate(['/pmc'], { queryParams: { athleteId } });
  }

  loadAthleteSchedule(athleteId: string) {
    const start = this.scheduleWeekStart.toISOString().split('T')[0];
    const end = this.scheduleWeekEnd.toISOString().split('T')[0];

    this.coachService.getAthleteSchedule(athleteId, start, end).subscribe({
      next: (data) => this.scheduleSubject.next(data),
      error: (err) => console.error('Error loading schedule', err)
    });
  }

  getWorkoutTitle(workout: ScheduledWorkout): string {
    return workout.trainingTitle || workout.title || 'W-' + workout.trainingId.substring(0, 8);
  }

  getWorkoutDuration(workout: ScheduledWorkout): string {
    if (workout.totalDurationSeconds) {
      const totalSec = workout.totalDurationSeconds;
      const h = Math.floor(totalSec / 3600);
      const m = Math.floor((totalSec % 3600) / 60);
      const s = totalSec % 60;
      return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    }
    return workout.duration || '-';
  }

  removeTag(athlete: User | null, tag: string) {
    if (!athlete) return;
    this.coachService.removeAthleteTag(athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.tags = updated.tags;
        this.loadTags();
      },
      error: () => {
        if (athlete.tags) {
          athlete.tags = athlete.tags.filter(t => t !== tag);
        }
      }
    });
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  addAthlete() {
    this.isInviteCodeModalOpen = true;
  }

  onCodeGenerated() {
    this.loadTags();
  }

  openShareModal() {
    this.coachTrainings$.subscribe(trainings => {
      if (trainings.length > 0) {
        this.trainingToShare = trainings[0];
        this.isShareModalOpen = true;
      }
    }).unsubscribe;
  }

  onTrainingShared() {
    this.isShareModalOpen = false;
  }

  assignWorkout() {
    if (!this.selectedAthlete) return;
    this.isScheduleModalOpen = true;
  }

  onScheduled() {
    this.isScheduleModalOpen = false;
    if (this.selectedAthlete) {
      this.loadAthleteSchedule(this.selectedAthlete.id);
    }
  }

  getPowerZones(ftp: number | undefined): { name: string; low: number; high: number | null; color: string }[] {
    if (!ftp) return [];
    return [
      { name: 'Z1 — Active Recovery', low: 0,                          high: Math.round(ftp * 0.55),       color: '#60a5fa' },
      { name: 'Z2 — Endurance',       low: Math.round(ftp * 0.55) + 1, high: Math.round(ftp * 0.75),       color: '#34d399' },
      { name: 'Z3 — Tempo',           low: Math.round(ftp * 0.75) + 1, high: Math.round(ftp * 0.90),       color: '#fbbf24' },
      { name: 'Z4 — Threshold',       low: Math.round(ftp * 0.90) + 1, high: Math.round(ftp * 1.05),       color: '#f97316' },
      { name: 'Z5 — VO2Max',          low: Math.round(ftp * 1.05) + 1, high: Math.round(ftp * 1.20),       color: '#f43f5e' },
      { name: 'Z6 — Anaerobic',       low: Math.round(ftp * 1.20) + 1, high: null,                         color: '#a855f7' },
    ];
  }

  getEstimatedVo2Max(ftp: number | undefined): string {
    if (!ftp) return '—';
    return ((ftp / 0.757) * (10.8 / 70)).toFixed(1);
  }

  getSportDistribution(sessions: any[]): { sport: string; count: number; pct: number }[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({ sport, count, pct: Math.round(count / sessions.length * 100) }))
      .sort((a, b) => b.count - a.count);
  }

  formatSessionDur(sec: number): string {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
}
