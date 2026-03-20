import {
  Component,
  DestroyRef,
  EventEmitter,
  inject,
  Input,
  NgZone,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ActionContext, ActionResult, AIActionService, AIActionType} from '../../../services/ai-action.service';
import {ClubGroup, ClubService} from '../../../services/club.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {TrainingService} from '../../../services/training.service';
import {Training, SPORT_OPTIONS, SportFilter} from '../../../models/training.model';
import {CalendarService} from '../../../services/calendar.service';
import {CoachService} from '../../../services/coach.service';
import {AuthService, User} from '../../../services/auth.service';

export type TrainingActionMode = 'session' | 'self-schedule' | 'coach-assign' | 'group-assign';

@Component({
  selector: 'app-training-action-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './training-action-modal.component.html',
  styleUrl: './training-action-modal.component.css',
})
export class TrainingActionModalComponent implements OnInit, OnChanges {
  @Input() isOpen = false;
  @Input() mode: TrainingActionMode = 'session';
  @Input() preselectedDate?: string;
  @Input() preselectedAthletes?: User[];
  @Input() clubId?: string;
  @Input() groupId?: string;
  @Input() sessionId?: string;
  @Input() sessionInfo: { scheduledAt?: string; sport?: string; clubGroupName?: string } | null = null;
  @Input() preselectedTrainingId?: string;

  @Output() closed = new EventEmitter<void>();
  @Output() completed = new EventEmitter<{ success: boolean; content?: string }>();

  private aiActionService = inject(AIActionService);
  private clubService = inject(ClubService);
  private zoneService = inject(ZoneService);
  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private coachService = inject(CoachService);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);
  private destroyRef = inject(DestroyRef);

  // Tab state
  tab: 'ai' | 'select' = 'ai';

  // AI generation fields
  prompt = '';
  selectedSport: SportFilter = 'CYCLING';
  selectedZoneSystemId = '';
  allZoneSystems: ZoneSystem[] = [];
  filteredZoneSystems: ZoneSystem[] = [];
  availableGroups: ClubGroup[] = [];
  selectedGroupId = '';
  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING'];
  readonly sportOptions = SPORT_OPTIONS;

  // Select existing fields
  availableTrainings: Training[] = [];
  selectedTrainingId: string | null = null;
  searchQuery = '';

  // Schedule/assign fields
  selectedDate = '';
  notes = '';
  selectedAthleteIds: string[] = [];
  availableAthletes: User[] = [];
  availableTags: string[] = [];
  activeTags: Set<string> = new Set();
  private clubGroups: { id: string; name: string; memberIds: string[] }[] = [];

  // State
  loading = false;
  errorMessage = '';
  successMessage = '';
  private userId = '';

  get modalTitle(): string {
    switch (this.mode) {
      case 'session': return 'Link Training';
      case 'self-schedule': return 'Schedule Workout';
      case 'coach-assign':
      case 'group-assign': return 'Assign Workout';
    }
  }

  get submitLabel(): string {
    if (this.tab === 'ai') return 'Generate';
    switch (this.mode) {
      case 'session': return 'Link Training';
      case 'self-schedule': return 'Schedule';
      case 'coach-assign': return 'Assign';
      case 'group-assign': return `Assign to ${this.selectedAthleteIds.length} Athletes`;
    }
  }

  get loadingLabel(): string {
    if (this.tab === 'ai') return 'Generating...';
    switch (this.mode) {
      case 'session': return 'Linking...';
      case 'self-schedule': return 'Scheduling...';
      case 'coach-assign':
      case 'group-assign': return 'Assigning...';
    }
  }

  get showSessionBanner(): boolean {
    return this.mode === 'session' && !!this.sessionInfo;
  }

  get showDatePicker(): boolean {
    if (this.mode === 'session') return false;
    if (this.mode === 'self-schedule' && !this.preselectedDate) return false;
    return true;
  }

  get showAthleteSelect(): boolean {
    return this.mode === 'group-assign';
  }

  get showAthleteChip(): boolean {
    return this.mode === 'coach-assign' && !!this.preselectedAthletes?.length;
  }

  get showTagFilter(): boolean {
    return this.mode === 'group-assign' && this.availableTags.length > 0;
  }

  get showNotes(): boolean {
    return this.mode !== 'session';
  }

  get showTabs(): boolean {
    if (this.preselectedTrainingId) return false;
    if (this.mode === 'self-schedule' && !this.preselectedDate) return false;
    return true;
  }

  get showSportSelector(): boolean {
    return true;
  }

  get filteredTrainings(): Training[] {
    if (!this.searchQuery.trim()) return this.availableTrainings;
    const q = this.searchQuery.toLowerCase();
    return this.availableTrainings.filter(
      (t) =>
        t.title?.toLowerCase().includes(q) ||
        t.description?.toLowerCase().includes(q)
    );
  }

  get canSubmit(): boolean {
    if (this.loading) return false;

    if (this.tab === 'ai') {
      return !!this.prompt.trim() && !!this.selectedSport;
    }

    // Select tab
    if (!this.selectedTrainingId) return false;

    if (this.showDatePicker && !this.selectedDate) return false;

    if (this.mode === 'coach-assign' && (!this.preselectedAthletes || this.preselectedAthletes.length === 0)) return false;
    if (this.mode === 'group-assign' && this.selectedAthleteIds.length === 0) return false;

    return true;
  }

  ngOnInit(): void {
    this.trainingService.trainings$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(t => {
      this.ngZone.run(() => (this.availableTrainings = t));
    });
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
      if (u) this.userId = u.id;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetState();
      this.trainingService.loadTrainings();
      this.loadZoneSystems();

      if (this.preselectedDate) {
        this.selectedDate = this.preselectedDate;
      }
      if (this.preselectedTrainingId) {
        this.selectedTrainingId = this.preselectedTrainingId;
        this.tab = 'select';
      }
      if (this.preselectedAthletes?.length) {
        this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
      }

      // Load club groups for AI tag selector
      if (this.clubId) {
        this.clubService.loadGroups(this.clubId);
        this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
          this.ngZone.run(() => {
            this.availableGroups = groups;
            this.clubGroups = groups;
          });
        });
      }

      // Load athletes for group-assign mode
      if (this.mode === 'group-assign' && this.clubId) {
        this.clubService.loadMembers(this.clubId);
        this.clubService.members$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(members => {
          this.ngZone.run(() => {
            this.availableAthletes = members.map(m => ({
              id: m.userId,
              displayName: m.displayName || m.userId,
              profilePicture: m.profilePicture,
              groups: [] as string[],
            } as unknown as User)).sort((a, b) => a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' }));
            this.enrichAthletesWithGroups();
            // Restore preselected athletes after members load
            if (this.preselectedAthletes?.length) {
              this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
            }
          });
        });
        this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
          this.ngZone.run(() => {
            this.availableTags = groups.map(g => g.name);
            this.clubGroups = groups;
            this.enrichAthletesWithGroups();
          });
        });
      }
    }
  }

  setTab(tab: 'ai' | 'select'): void {
    this.tab = tab;
    this.errorMessage = '';
    this.successMessage = '';
  }

  selectTraining(t: Training): void {
    this.selectedTrainingId = this.selectedTrainingId === t.id ? null : t.id;
  }

  onSportChange(): void {
    this.filteredZoneSystems = this.allZoneSystems.filter(z => z.sportType === this.selectedSport);
    const defaultSystem = this.filteredZoneSystems.find(z => z.defaultForSport);
    this.selectedZoneSystemId = defaultSystem?.id ?? '';
  }

  // --- Athlete / Tag management (group-assign) ---

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
      const taggedIds = this.availableAthletes.filter(a => a.groups?.includes(tag)).map(a => a.id);
      this.selectedAthleteIds = this.selectedAthleteIds.filter(id => !taggedIds.includes(id));
    } else {
      this.activeTags.add(tag);
      const taggedIds = this.availableAthletes.filter(a => a.groups?.includes(tag)).map(a => a.id);
      for (const id of taggedIds) {
        if (!this.selectedAthleteIds.includes(id)) {
          this.selectedAthleteIds.push(id);
        }
      }
    }
  }

  // --- Submit ---

  submit(): void {
    if (!this.canSubmit) return;

    if (this.tab === 'ai') {
      this.submitAi();
    } else {
      this.submitSelect();
    }
  }

  close(): void {
    if (this.loading) return;
    this.closed.emit();
  }

  // --- Private ---

  private submitAi(): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    if (this.mode === 'session') {
      // Session mode: AI creates + links in one step
      const ctx: ActionContext = {
        clubId: this.clubId,
        sessionId: this.sessionId,
        clubGroupId: this.selectedGroupId || undefined,
        sport: this.selectedSport || undefined,
        zoneSystemId: this.selectedZoneSystemId || undefined,
      };
      this.aiActionService.executeAction(this.prompt.trim(), 'TRAINING_WITH_SESSION', ctx).subscribe({
        next: (result) => {
          this.ngZone.run(() => {
            this.loading = false;
            if (result.success) {
              this.successMessage = result.content;
              this.completed.emit({ success: true, content: result.content });
            } else {
              this.errorMessage = result.content;
            }
          });
        },
        error: (err) => {
          this.ngZone.run(() => {
            this.loading = false;
            this.errorMessage = err?.error?.message ?? 'An unexpected error occurred.';
          });
        },
      });
    } else {
      // Non-session modes: AI generates training, then switch to select tab
      const ctx: ActionContext = {
        sport: this.selectedSport || undefined,
        zoneSystemId: this.selectedZoneSystemId || undefined,
        clubId: this.clubId,
        clubGroupId: this.selectedGroupId || undefined,
        coachGroupId: this.groupId,
      };
      this.aiActionService.executeAction(this.prompt.trim(), 'TRAINING_FROM_NOTATION', ctx).subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loading = false;
            this.prompt = '';
            this.successMessage = 'Training created! Select it below to continue.';
            this.trainingService.loadTrainings();
            this.tab = 'select';
          });
        },
        error: (err) => {
          this.ngZone.run(() => {
            this.loading = false;
            this.errorMessage = err?.error?.message ?? 'Failed to generate training.';
          });
        },
      });
    }
  }

  private submitSelect(): void {
    if (!this.selectedTrainingId) return;

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    switch (this.mode) {
      case 'session':
        this.submitSessionLink();
        break;
      case 'self-schedule':
        this.submitSelfSchedule();
        break;
      case 'coach-assign':
        this.submitCoachAssign();
        break;
      case 'group-assign':
        this.submitGroupAssign();
        break;
    }
  }

  private submitSessionLink(): void {
    if (!this.clubId || !this.sessionId || !this.selectedTrainingId) return;
    this.clubService.linkTrainingToSession(this.clubId, this.sessionId, this.selectedTrainingId).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.loading = false;
          this.successMessage = 'Training linked to session successfully.';
          this.completed.emit({ success: true, content: 'Training linked.' });
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.errorMessage = err?.error?.message ?? 'Failed to link training.';
        });
      },
    });
  }

  private submitSelfSchedule(): void {
    if (!this.selectedTrainingId) return;
    this.calendarService.scheduleWorkout(this.selectedTrainingId, this.selectedDate, this.notes || undefined).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.loading = false;
          this.completed.emit({ success: true });
          this.closed.emit();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.errorMessage = err?.error?.message ?? 'Failed to schedule workout.';
        });
      },
    });
  }

  private submitCoachAssign(): void {
    if (!this.selectedTrainingId || !this.preselectedAthletes?.length) return;
    const athleteIds = this.preselectedAthletes.map(a => a.id);
    this.coachService.assignTraining(this.selectedTrainingId, athleteIds, this.selectedDate, this.notes || undefined).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.loading = false;
          this.completed.emit({ success: true });
          this.closed.emit();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.errorMessage = err?.error?.message ?? 'Failed to assign training.';
        });
      },
    });
  }

  private submitGroupAssign(): void {
    if (!this.selectedTrainingId || this.selectedAthleteIds.length === 0) return;
    this.coachService.assignTraining(
      this.selectedTrainingId,
      this.selectedAthleteIds,
      this.selectedDate,
      this.notes || undefined,
      this.clubId,
      this.groupId
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.loading = false;
          this.completed.emit({ success: true });
          this.closed.emit();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.loading = false;
          this.errorMessage = err?.error?.message ?? 'Failed to assign training.';
        });
      },
    });
  }

  private loadZoneSystems(): void {
    this.zoneService.getMyZoneSystems().subscribe({
      next: (systems) => {
        this.ngZone.run(() => {
          this.allZoneSystems = systems;
          this.selectedSport = 'CYCLING';
          this.onSportChange();
        });
      },
      error: () => {
        this.allZoneSystems = [];
        this.selectedSport = 'CYCLING';
      },
    });
  }

  private enrichAthletesWithGroups(): void {
    if (!this.clubGroups.length || !this.availableAthletes.length) return;
    for (const athlete of this.availableAthletes) {
      athlete.groups = this.clubGroups
        .filter(g => g.memberIds.includes(athlete.id))
        .map(g => g.name);
    }
  }

  private resetState(): void {
    this.tab = 'ai';
    this.prompt = '';
    this.selectedSport = 'CYCLING';
    this.selectedZoneSystemId = '';
    this.allZoneSystems = [];
    this.filteredZoneSystems = [];
    this.availableGroups = [];
    this.selectedGroupId = '';
    this.selectedTrainingId = null;
    this.searchQuery = '';
    this.selectedDate = '';
    this.notes = '';
    this.selectedAthleteIds = [];
    this.availableAthletes = [];
    this.availableTags = [];
    this.activeTags = new Set();
    this.clubGroups = [];
    this.loading = false;
    this.errorMessage = '';
    this.successMessage = '';
  }
}
