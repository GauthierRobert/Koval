import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, NgZone, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TrainingService } from '../../../services/training.service';
import { Training, SPORT_OPTIONS, SportFilter } from '../../../models/training.model';
import { CalendarService } from '../../../services/calendar.service';
import { CoachService } from '../../../services/coach.service';
import { AuthService, User } from '../../../services/auth.service';
import { ClubService } from '../../../services/club.service';
import { AIActionService, AIActionType, ActionContext } from '../../../services/ai-action.service';

@Component({
  selector: 'app-schedule-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './schedule-modal.component.html',
  styleUrl: './schedule-modal.component.css',
})
export class ScheduleModalComponent implements OnInit, OnChanges {
  @Input() isOpen = false;
  @Input() preselectedTraining: Training | null = null;
  @Input() preselectedDate: string | null = null;
  @Input() preselectedAthletes: User[] | null = null;
  @Input() mode: 'athlete' | 'coach' = 'athlete';
  @Input() groupId: string | null = null;
  @Input() clubId: string | null = null;
  @Input() preselectedGroupName: string | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() scheduled = new EventEmitter<void>();

  trainings: Training[] = [];
  availableAthletes: User[] = [];
  availableTags: string[] = [];
  activeTags: Set<string> = new Set();
  selectedTrainingId = '';
  selectedDate = '';
  selectedAthleteIds: string[] = [];
  notes = '';

  showAiGenerate = false;
  aiPrompt = '';
  aiSport: SportFilter = 'CYCLING';
  aiLoading = false;
  readonly sportOptions = SPORT_OPTIONS;

  private userId = '';
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private trainingService: TrainingService,
    private calendarService: CalendarService,
    private coachService: CoachService,
    private authService: AuthService,
    private clubService: ClubService,
    private aiActionService: AIActionService,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.trainingService.trainings$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(t => (this.trainings = t));
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
      if (u) this.userId = u.id;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetForm();

      if (this.preselectedTraining) {
        this.selectedTrainingId = this.preselectedTraining.id;
      }
      if (this.preselectedDate) {
        this.selectedDate = this.preselectedDate;
      }
      if (this.preselectedAthletes && this.preselectedAthletes.length > 0) {
        this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
      }

      if (this.mode === 'coach' && this.userId) {
        if (this.clubId) {
          // Load club members instead of coach athletes
          this.clubService.loadMembers(this.clubId);
          this.clubService.members$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(members => {
            this.ngZone.run(() => {
              this.availableAthletes = members.map(m => ({
                id: m.userId,
                displayName: m.displayName || m.userId,
                profilePicture: m.profilePicture,
              } as User)).sort((a, b) => a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' }));
              if (this.preselectedAthletes && this.preselectedAthletes.length > 0) {
                this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
              }
            });
          });
          this.clubService.loadGroups(this.clubId);
          this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
            this.ngZone.run(() => {
              this.availableTags = groups.map(g => g.name);
              if (this.preselectedGroupName && this.availableTags.includes(this.preselectedGroupName)) {
                this.toggleTag(this.preselectedGroupName);
              }
            });
          });
        } else {
          this.coachService.getAthletes().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(athletes => {
            this.ngZone.run(() => {
              this.availableAthletes = athletes.sort((a, b) => a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' }));
              if (this.preselectedAthletes && this.preselectedAthletes.length > 0) {
                this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
              }
            });
          });
          this.coachService.getAllGroups().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
            this.ngZone.run(() => {
              this.availableTags = groups.map(g => g.name);
              if (this.preselectedGroupName && this.availableTags.includes(this.preselectedGroupName)) {
                this.toggleTag(this.preselectedGroupName);
              }
            });
          });
        }
      }
    }
  }

  isAthleteSelected(id: string): boolean {
    return this.selectedAthleteIds.includes(id);
  }

  toggleAthlete(id: string): void {
    const idx = this.selectedAthleteIds.indexOf(id);
    if (idx >= 0) {
      this.selectedAthleteIds.splice(idx, 1);
    } else {
      this.selectedAthleteIds.push(id);
    }
  }

  isTagActive(tag: string): boolean {
    return this.activeTags.has(tag);
  }

  toggleTag(tag: string): void {
    if (this.activeTags.has(tag)) {
      this.activeTags.delete(tag);
      // Deselect athletes that have this group
      const taggedIds = this.availableAthletes
        .filter(a => a.groups?.includes(tag))
        .map(a => a.id);
      this.selectedAthleteIds = this.selectedAthleteIds.filter(id => !taggedIds.includes(id));
    } else {
      this.activeTags.add(tag);
      // Select all athletes with this group
      const taggedIds = this.availableAthletes
        .filter(a => a.groups?.includes(tag))
        .map(a => a.id);
      for (const id of taggedIds) {
        if (!this.selectedAthleteIds.includes(id)) {
          this.selectedAthleteIds.push(id);
        }
      }
    }
  }

  canSubmit(): boolean {
    if (this.showAiGenerate) return false;
    if (!this.selectedTrainingId || !this.selectedDate) return false;
    if (this.mode === 'coach' && this.selectedAthleteIds.length === 0) return false;
    return true;
  }

  submit(): void {
    if (!this.canSubmit()) return;

    if (this.mode === 'coach') {
      this.coachService
        .assignTraining(this.selectedTrainingId, this.selectedAthleteIds, this.selectedDate, this.notes || undefined, this.clubId ?? undefined, this.groupId ?? undefined)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.ngZone.run(() => {
              this.scheduled.emit();
              this.close();
            });
          },
          error: (err) => console.error('Failed to assign training', err),
        });
    } else {
      this.calendarService
        .scheduleWorkout(this.selectedTrainingId, this.selectedDate, this.notes || undefined)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.ngZone.run(() => {
              this.scheduled.emit();
              this.close();
            });
          },
          error: (err) => console.error('Failed to schedule workout', err),
        });
    }
  }

  close(): void {
    this.closed.emit();
  }

  generateWithAi(): void {
    if (!this.aiPrompt.trim() || this.aiLoading) return;
    this.aiLoading = true;
    const context: ActionContext = {
      sport: this.aiSport ?? undefined,
      clubId: this.clubId ?? undefined,
      coachGroupId: this.groupId ?? undefined,
    };
    this.aiActionService.executeAction(this.aiPrompt, 'TRAINING_FROM_NOTATION' as AIActionType, context).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.aiLoading = false;
          this.aiPrompt = '';
          this.showAiGenerate = false;
          // Reload trainings so new one appears in dropdown
          this.trainingService.loadTrainings();
        });
      },
      error: () => {
        this.ngZone.run(() => { this.aiLoading = false; });
      },
    });
  }

  private resetForm(): void {
    this.selectedTrainingId = '';
    this.selectedDate = '';
    this.selectedAthleteIds = [];
    this.notes = '';
    this.availableAthletes = [];
    this.availableTags = [];
    this.activeTags = new Set();
    this.showAiGenerate = false;
    this.aiPrompt = '';
    this.aiLoading = false;
  }
}
