import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActionContext, ActionResult, AIActionService, AIActionType} from '../../../services/ai-action.service';
import {ClubGroup, ClubService} from '../../../services/club.service';
import {ClubSessionService} from '../../../services/club-session.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {TrainingService} from '../../../services/training.service';
import {Training, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType} from '../../../models/training.model';
import {SPORT_BANNER_COLORS} from '../../../models/plan.model';
import {BehaviorSubject, combineLatest, map, Observable} from 'rxjs';

interface ModalState {
  loading: boolean;
  errorMessage: string;
  aiMessage: string;
  successMessage: string;
}

@Component({
  selector: 'app-create-with-ai-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './create-with-ai-modal.component.html',
  styleUrl: './create-with-ai-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateWithAiModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() actionType: AIActionType = 'TRAINING_WITH_SESSION';
  @Input() context: ActionContext = {};
  @Input() label = 'Create with AI';
  @Input() sessionInfo: { scheduledAt?: string; sport?: string; clubGroupName?: string } | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() created = new EventEmitter<ActionResult>();

  private aiActionService = inject(AIActionService);
  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private zoneService = inject(ZoneService);
  private trainingService = inject(TrainingService);
  private translate = inject(TranslateService);

  // Reactive state
  private stateSubject = new BehaviorSubject<ModalState>({
    loading: false,
    errorMessage: '',
    aiMessage: '',
    successMessage: '',
  });
  state$ = this.stateSubject.asObservable();

  private groupsSubject = new BehaviorSubject<ClubGroup[]>([]);
  groups$ = this.groupsSubject.asObservable();

  private allZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  private filteredZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  filteredZoneSystems$ = this.filteredZoneSystemsSubject.asObservable();

  private trainingsSubject = new BehaviorSubject<Training[]>([]);
  trainings$ = this.trainingsSubject.asObservable();
  private searchQuerySubject = new BehaviorSubject<string>('');
  filteredTrainings$: Observable<Training[]> = combineLatest([
    this.trainingsSubject,
    this.searchQuerySubject,
  ]).pipe(
    map(([trainings, query]) => {
      if (!query.trim()) return trainings;
      const q = query.toLowerCase();
      return trainings.filter(
        (t) => t.title?.toLowerCase().includes(q) || t.description?.toLowerCase().includes(q),
      );
    }),
  );

  prompt = '';
  selectedGroupId = '';
  selectedSport = '';
  selectedZoneSystemId = '';
  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING'];
  mode: 'ai' | 'select' = 'ai';
  selectedTrainingId: string | null = null;
  searchQuery = '';

  get promptPlaceholder(): string {
    switch (this.actionType) {
      case 'ZONE_CREATION':
        return this.translate.instant('CREATE_WITH_AI.PLACEHOLDER_PROMPT_ZONE_CREATION');
      case 'TRAINING_CREATION':
        return this.translate.instant('CREATE_WITH_AI.PLACEHOLDER_PROMPT_TRAINING_NOTATION');
      default:
        return this.translate.instant('CREATE_WITH_AI.PLACEHOLDER_PROMPT_DEFAULT');
    }
  }

  get showSportSelector(): boolean {
    return this.actionType === 'TRAINING_CREATION' || this.actionType === 'TRAINING_WITH_SESSION';
  }

  get showTagSelector(): boolean {
    return (this.actionType === 'TRAINING_WITH_SESSION' || this.actionType === 'TRAINING_CREATION')
        && !!this.context.clubId;
  }

  get showSelectTab(): boolean {
    return !!this.context.sessionId && !!this.context.clubId;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetState();
      if (this.showTagSelector) {
        this.clubService.loadGroups(this.context.clubId!);
        this.clubService.groups$.subscribe((groups) => this.groupsSubject.next(groups));
      }
      if (this.showSportSelector) {
        this.zoneService.getMyZoneSystems().subscribe({
          next: (systems) => {
            this.allZoneSystemsSubject.next(systems);
            this.selectedSport = 'CYCLING';
            this.onSportChange();
          },
          error: () => {
            this.allZoneSystemsSubject.next([]);
            this.selectedSport = 'CYCLING';
          },
        });
      }
      if (this.showSelectTab) {
        this.trainingService.trainings$.subscribe((trainings) => this.trainingsSubject.next(trainings));
        this.trainingService.loadTrainings();
      }
    }
  }

  onSportChange(): void {
    const filtered = this.allZoneSystemsSubject.value.filter((z) => z.sportType === this.selectedSport);
    this.filteredZoneSystemsSubject.next(filtered);
    const defaultSystem = filtered.find((z) => z.defaultForSport);
    this.selectedZoneSystemId = defaultSystem?.id ?? '';
  }

  onSearchQueryChange(query: string): void {
    this.searchQuery = query;
    this.searchQuerySubject.next(query);
  }

  setMode(mode: 'ai' | 'select'): void {
    this.mode = mode;
    this.patchState({ errorMessage: '', aiMessage: '', successMessage: '' });
  }

  selectTraining(t: Training): void {
    this.selectedTrainingId = this.selectedTrainingId === t.id ? null : t.id;
  }

  confirmSelection(): void {
    const state = this.stateSubject.value;
    if (!this.selectedTrainingId || state.loading || !this.context.clubId || !this.context.sessionId) return;

    this.patchState({ loading: true, errorMessage: '', aiMessage: '', successMessage: '' });

    this.clubSessionService
      .linkTrainingToSession(this.context.clubId, this.context.sessionId, this.selectedTrainingId)
      .subscribe({
        next: () => {
          this.patchState({
            loading: false,
            successMessage: this.translate.instant('CREATE_WITH_AI.SUCCESS_TRAINING_LINKED'),
          });
          this.created.emit({ success: true, content: 'Training linked.' });
        },
        error: (err) => {
          this.patchState({
            loading: false,
            errorMessage: err?.error?.message ?? this.translate.instant('CREATE_WITH_AI.ERROR_FAILED_LINK'),
          });
        },
      });
  }

  submit(): void {
    const state = this.stateSubject.value;
    if (!this.prompt.trim() || state.loading) return;

    this.patchState({ loading: true, errorMessage: '', aiMessage: '', successMessage: '' });

    const ctx: ActionContext = {
      ...this.context,
      clubGroupId: this.selectedGroupId || this.context.clubGroupId,
      sport: this.selectedSport || undefined,
      zoneSystemId: this.selectedZoneSystemId || undefined,
    };

    this.aiActionService.executeAction(this.prompt.trim(), this.actionType, ctx).subscribe({
      next: (result) => {
        if (result.success) {
          this.patchState({ loading: false, successMessage: result.content });
          this.created.emit(result);
        } else {
          this.patchState({ loading: false, aiMessage: result.content });
        }
      },
      error: (err) => {
        this.patchState({
          loading: false,
          errorMessage: err?.error?.message ?? this.translate.instant('CREATE_WITH_AI.ERROR_UNEXPECTED'),
        });
      },
    });
  }

  close(): void {
    if (this.stateSubject.value.loading) return;
    this.closed.emit();
  }

  sportColor(sport: string): { bg: string; border: string; text: string } {
    return SPORT_BANNER_COLORS[sport] ?? { bg: 'rgba(255,157,0,0.15)', border: '#ff9d00', text: '#ff9d00' };
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  private patchState(patch: Partial<ModalState>): void {
    this.stateSubject.next({ ...this.stateSubject.value, ...patch });
  }

  private resetState(): void {
    this.prompt = '';
    this.selectedGroupId = '';
    this.selectedSport = '';
    this.selectedZoneSystemId = '';
    this.mode = 'ai';
    this.selectedTrainingId = null;
    this.searchQuery = '';
    this.stateSubject.next({ loading: false, errorMessage: '', aiMessage: '', successMessage: '' });
    this.groupsSubject.next([]);
    this.allZoneSystemsSubject.next([]);
    this.filteredZoneSystemsSubject.next([]);
    this.trainingsSubject.next([]);
    this.searchQuerySubject.next('');
  }
}
