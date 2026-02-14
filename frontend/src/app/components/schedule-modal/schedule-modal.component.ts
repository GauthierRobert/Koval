import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Training, TrainingService } from '../../services/training.service';
import { CalendarService } from '../../services/calendar.service';
import { CoachService } from '../../services/coach.service';
import { AuthService, User } from '../../services/auth.service';

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

  private userId = '';

  constructor(
    private trainingService: TrainingService,
    private calendarService: CalendarService,
    private coachService: CoachService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.trainingService.trainings$.subscribe(t => (this.trainings = t));
    this.authService.user$.subscribe(u => {
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
        this.coachService.getAthletes().subscribe(athletes => {
          this.availableAthletes = athletes;
          // Re-apply preselection after athletes load
          if (this.preselectedAthletes && this.preselectedAthletes.length > 0) {
            this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
          }
        });
        this.coachService.getAllTags().subscribe(tags => {
          this.availableTags = tags.map(t => t.name);
        });
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
      // Deselect athletes that have this tag
      const taggedIds = this.availableAthletes
        .filter(a => a.tags?.includes(tag))
        .map(a => a.id);
      this.selectedAthleteIds = this.selectedAthleteIds.filter(id => !taggedIds.includes(id));
    } else {
      this.activeTags.add(tag);
      // Select all athletes with this tag
      const taggedIds = this.availableAthletes
        .filter(a => a.tags?.includes(tag))
        .map(a => a.id);
      for (const id of taggedIds) {
        if (!this.selectedAthleteIds.includes(id)) {
          this.selectedAthleteIds.push(id);
        }
      }
    }
  }

  canSubmit(): boolean {
    if (!this.selectedTrainingId || !this.selectedDate) return false;
    if (this.mode === 'coach' && this.selectedAthleteIds.length === 0) return false;
    return true;
  }

  submit(): void {
    if (!this.canSubmit()) return;

    if (this.mode === 'coach') {
      this.coachService
        .assignTraining(this.selectedTrainingId, this.selectedAthleteIds, this.selectedDate, this.notes || undefined)
        .subscribe({
          next: () => {
            this.scheduled.emit();
            this.close();
          },
          error: (err) => console.error('Failed to assign training', err),
        });
    } else {
      this.calendarService
        .scheduleWorkout(this.selectedTrainingId, this.selectedDate, this.notes || undefined)
        .subscribe({
          next: () => {
            this.scheduled.emit();
            this.close();
          },
          error: (err) => console.error('Failed to schedule workout', err),
        });
    }
  }

  close(): void {
    this.closed.emit();
  }

  private resetForm(): void {
    this.selectedTrainingId = '';
    this.selectedDate = '';
    this.selectedAthleteIds = [];
    this.notes = '';
    this.availableAthletes = [];
    this.availableTags = [];
    this.activeTags = new Set();
  }
}
