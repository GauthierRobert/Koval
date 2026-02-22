import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, Observable, of, map } from 'rxjs';
import { CoachService, ScheduledWorkout } from '../../services/coach.service';
import { AuthService, User } from '../../services/auth.service';
import { Tag } from '../../services/tag.service';
import { PmcDataPoint } from '../../services/metrics.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { InviteCodeModalComponent } from '../invite-code-modal/invite-code-modal.component';
import { ShareTrainingModalComponent } from '../share-training-modal/share-training-modal.component';
import { Training, TrainingService, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { PmcChartComponent } from '../pmc-chart/pmc-chart.component';

import { RouterModule } from '@angular/router';
import { ZoneManagerComponent } from '../zone-manager/zone-manager.component';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ScheduleModalComponent, InviteCodeModalComponent, ShareTrainingModalComponent, ZoneManagerComponent, SportIconComponent, PmcChartComponent],
  templateUrl: './coach-dashboard.component.html',
  styleUrl: './coach-dashboard.component.css',
})
export class CoachDashboardComponent implements OnInit {
  selectedAthlete: User | null = null;
  isScheduleModalOpen = false;
  isInviteCodeModalOpen = false;
  isShareModalOpen = false;
  showZoneManager = false;
  trainingToShare: Training | null = null;
  activeTagFilter: string | null = null;
  newTagInput = '';

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
    private trainingService: TrainingService
  ) { }

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      if (u) {
        this.userId = u.id;
        this.loadAthletes();
        this.loadTags();
      }
    });
    this.coachTrainings$ = this.trainingService.trainings$;
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
    const to = new Date().toISOString().split('T')[0];
    const from = (() => { const d = new Date(); d.setMonth(d.getMonth() - 3); return d.toISOString().split('T')[0]; })();
    this.coachService.getAthletePmc(athleteId, from, to).subscribe({
      next: (data) => this.athletePmcSubject.next(data),
      error: () => this.athletePmcSubject.next([]),
    });
  }

  loadAthleteSchedule(athleteId: string) {
    const now = new Date();
    const start = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];
    const end = new Date(now.getTime() + (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];

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

  addTag(athlete: User | null) {
    if (!athlete || !this.newTagInput.trim()) return;
    const tag = this.newTagInput.trim();
    this.coachService.addAthleteTag(athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.tags = updated.tags;
        this.newTagInput = '';
        this.loadTags();
      },
      error: () => {
        if (!athlete.tags) athlete.tags = [];
        if (!athlete.tags.includes(tag)) athlete.tags.push(tag);
        this.newTagInput = '';
      }
    });
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
}
