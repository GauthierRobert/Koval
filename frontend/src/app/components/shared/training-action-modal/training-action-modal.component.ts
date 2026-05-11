import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
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
  HostListener,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {A11yModule} from '@angular/cdk/a11y';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AIActionService} from '../../../services/ai-action.service';
import {ClubGroup, ClubService, GroupLinkedTraining} from '../../../services/club.service';
import {ClubSessionService} from '../../../services/club-session.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {TrainingService} from '../../../services/training.service';
import {Training, SPORT_OPTIONS, SportFilter} from '../../../models/training.model';
import {CalendarService} from '../../../services/calendar.service';
import {CoachService} from '../../../services/coach.service';
import {AuthService, User} from '../../../services/auth.service';
import {AiPromptFormComponent} from './ai-prompt-form/ai-prompt-form.component';
import {TrainingSearchListComponent} from './training-search-list/training-search-list.component';
import {AthleteTagSelectorComponent} from './athlete-tag-selector/athlete-tag-selector.component';
import {TrainingActionMode} from './training-action-mode.type';
import {
  canLinkTraining as canLinkTrainingFn,
  enrichAthletesWithGroups,
  getLoadingLabel,
  getModalTitle,
  getSubmitLabel,
  sessionAvailableGroups as sessionAvailableGroupsFn,
  sessionShowNoGroupOption as sessionShowNoGroupOptionFn,
  toggleTagSelection,
} from './training-action-modal.helpers';
import {submitAi as submitAiFlow, submitSelect as submitSelectFlow, SubmitDeps} from './training-action-modal.submit';

export type {TrainingActionMode};

@Component({
  selector: 'app-training-action-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, A11yModule, AiPromptFormComponent, TrainingSearchListComponent, AthleteTagSelectorComponent],
  templateUrl: './training-action-modal.component.html',
  styleUrl: './training-action-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  @Input() existingLinkedTrainings: GroupLinkedTraining[] = [];
  @Input() sessionGroupId?: string;

  @Output() closed = new EventEmitter<void>();
  @Output() completed = new EventEmitter<{ success: boolean; content?: string }>();

  private aiActionService = inject(AIActionService);
  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private zoneService = inject(ZoneService);
  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private coachService = inject(CoachService);
  private authService = inject(AuthService);
  private ngZone = inject(NgZone);
  private destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);
  private cdr = inject(ChangeDetectorRef);

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

  get modalTitle(): string { return getModalTitle(this.mode, this.translate); }
  get submitLabel(): string { return getSubmitLabel(this.mode, this.tab, this.selectedAthleteIds.length, this.translate); }
  get loadingLabel(): string { return getLoadingLabel(this.mode, this.tab, this.translate); }

  get showSessionBanner(): boolean { return this.mode === 'session' && !!this.sessionInfo; }
  get showDatePicker(): boolean { return this.mode !== 'session'; }
  get showAthleteSelect(): boolean { return this.mode === 'group-assign'; }
  get showAthleteChip(): boolean { return this.mode === 'coach-assign' && !!this.preselectedAthletes?.length; }
  get showTagFilter(): boolean { return this.mode === 'group-assign' && this.availableTags.length > 0; }
  get showNotes(): boolean { return this.mode !== 'session'; }
  get showTabs(): boolean { return !this.preselectedTrainingId; }

  get sessionAvailableGroups(): ClubGroup[] {
    return sessionAvailableGroupsFn(this.mode, this.availableGroups, this.existingLinkedTrainings, this.sessionGroupId);
  }

  get sessionShowNoGroupOption(): boolean {
    return sessionShowNoGroupOptionFn(this.mode, this.existingLinkedTrainings);
  }

  get canLinkTraining(): boolean {
    return canLinkTrainingFn(this.mode, this.availableGroups, this.existingLinkedTrainings, this.sessionGroupId);
  }

  get canSubmit(): boolean {
    if (this.loading) return false;
    if (this.mode === 'session' && !this.canLinkTraining) return false;

    if (this.tab === 'ai') {
      return !!this.prompt.trim() && !!this.selectedSport;
    }

    // Select tab
    if (!this.selectedTrainingId) return false;

    if (this.showDatePicker && !this.selectedDate) return false;

    if (this.mode === 'coach-assign' && (!this.preselectedAthletes || this.preselectedAthletes.length === 0)) return false;
    return !(this.mode === 'group-assign' && this.selectedAthleteIds.length === 0);


  }

  ngOnInit(): void {
    this.trainingService.trainings$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(t => {
      this.ngZone.run(() => {
        this.availableTrainings = t;
        this.cdr.markForCheck();
      });
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
      } else if (this.mode === 'self-schedule') {
        this.selectedDate = new Date().toISOString().slice(0, 10);
      }
      if (this.preselectedTrainingId) {
        this.selectedTrainingId = this.preselectedTrainingId;
        this.tab = 'select';
      }
      if (this.preselectedAthletes?.length) {
        this.selectedAthleteIds = this.preselectedAthletes.map(a => a.id);
      }

      // Auto-select group when session belongs to a specific group
      if (this.sessionGroupId) {
        this.selectedGroupId = this.sessionGroupId;
      }

      // Load club groups for AI tag selector
      if (this.clubId) {
        this.clubService.loadGroups(this.clubId);
        this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
          this.ngZone.run(() => {
            this.availableGroups = groups;
            this.clubGroups = groups;
            this.cdr.markForCheck();
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
            this.cdr.markForCheck();
          });
        });
        this.clubService.groups$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
          this.ngZone.run(() => {
            this.availableTags = groups.map(g => g.name);
            this.clubGroups = groups;
            this.enrichAthletesWithGroups();
            this.cdr.markForCheck();
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

  toggleAthlete(id: string): void {
    const idx = this.selectedAthleteIds.indexOf(id);
    if (idx >= 0) {
      this.selectedAthleteIds.splice(idx, 1);
    } else {
      this.selectedAthleteIds.push(id);
    }
  }

  toggleTag(tag: string): void {
    this.selectedAthleteIds = toggleTagSelection(tag, this.activeTags, this.availableAthletes, this.selectedAthleteIds);
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen && !this.loading) {
      this.close();
    }
  }

  // --- Private ---

  private get submitDeps(): SubmitDeps {
    return {
      aiActionService: this.aiActionService,
      clubSessionService: this.clubSessionService,
      calendarService: this.calendarService,
      coachService: this.coachService,
      translate: this.translate,
      ngZone: this.ngZone,
    };
  }

  private get submitCallbacks() {
    return {
      setLoading: (v: boolean) => { this.loading = v; this.cdr.markForCheck(); },
      setError: (msg: string) => { this.errorMessage = msg; this.cdr.markForCheck(); },
      setSuccess: (msg: string) => { this.successMessage = msg; this.cdr.markForCheck(); },
      emitCompleted: (p: {success: boolean; content?: string}) => this.completed.emit(p),
      close: () => this.closed.emit(),
    };
  }

  private submitAi(): void {
    submitAiFlow(this.submitDeps, {
      ...this.submitCallbacks,
      onAiCreated: () => {
        this.prompt = '';
        this.trainingService.loadTrainings();
        this.tab = 'select';
        this.cdr.markForCheck();
      },
    }, {
      prompt: this.prompt,
      mode: this.mode,
      clubId: this.clubId,
      sessionId: this.sessionId,
      groupId: this.groupId,
      selectedGroupId: this.selectedGroupId,
      selectedSport: this.selectedSport,
      selectedZoneSystemId: this.selectedZoneSystemId,
    });
  }

  private submitSelect(): void {
    if (!this.selectedTrainingId) return;
    submitSelectFlow(this.submitDeps, this.submitCallbacks, {
      mode: this.mode,
      clubId: this.clubId,
      groupId: this.groupId,
      sessionId: this.sessionId,
      selectedTrainingId: this.selectedTrainingId,
      selectedGroupId: this.selectedGroupId,
      selectedDate: this.selectedDate,
      notes: this.notes,
      preselectedAthleteIds: this.preselectedAthletes?.map(a => a.id) ?? [],
      selectedAthleteIds: this.selectedAthleteIds,
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
    enrichAthletesWithGroups(this.availableAthletes, this.clubGroups as ClubGroup[]);
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
