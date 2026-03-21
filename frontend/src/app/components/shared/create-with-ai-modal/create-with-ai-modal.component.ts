import {Component, EventEmitter, inject, Input, NgZone, OnChanges, Output, SimpleChanges,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActionContext, ActionResult, AIActionService, AIActionType} from '../../../services/ai-action.service';
import {ClubGroup, ClubService} from '../../../services/club.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {TrainingService} from '../../../services/training.service';
import {Training} from '../../../models/training.model';

@Component({
  selector: 'app-create-with-ai-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './create-with-ai-modal.component.html',
  styleUrl: './create-with-ai-modal.component.css',
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
  private zoneService = inject(ZoneService);
  private trainingService = inject(TrainingService);
  private ngZone = inject(NgZone);

  prompt = '';
  loading = false;
  errorMessage = '';
  aiMessage = '';
  successMessage = '';

  availableGroups: ClubGroup[] = [];
  selectedGroupId = '';

  selectedSport = '';
  selectedZoneSystemId = '';
  allZoneSystems: ZoneSystem[] = [];
  filteredZoneSystems: ZoneSystem[] = [];
  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING'];

  // Select existing training tab
  mode: 'ai' | 'select' = 'ai';
  availableTrainings: Training[] = [];
  selectedTrainingId: string | null = null;
  searchQuery = '';

  get promptPlaceholder(): string {
    switch (this.actionType) {
      case 'ZONE_CREATION':
        return 'e.g. Create 7 power zones based on FTP for cycling, from Active Recovery to Neuromuscular';
      case 'TRAINING_FROM_NOTATION':
        return 'e.g. 1h sweet-spot session: 10min warm-up, 3x10min at 88-93% with 5min recovery, 10min cool-down';
      default:
        return 'e.g. Create a 60-min sweet-spot group ride for Tuesday evening at 7pm';
    }
  }

  get showSportSelector(): boolean {
    return this.actionType === 'TRAINING_FROM_NOTATION' || this.actionType === 'TRAINING_WITH_SESSION';
  }

  get showTagSelector(): boolean {
    return (this.actionType === 'TRAINING_WITH_SESSION' || this.actionType === 'TRAINING_FROM_NOTATION')
        && !!this.context.clubId;
  }

  get showSelectTab(): boolean {
    return !!this.context.sessionId && !!this.context.clubId;
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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetState();
      if (this.showTagSelector) {
        this.clubService.loadGroups(this.context.clubId!);
        this.clubService.groups$.subscribe((groups) => {
          this.ngZone.run(() => (this.availableGroups = groups));
        });
      }
      if (this.showSportSelector) {
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
      if (this.showSelectTab) {
        this.trainingService.trainings$.subscribe((trainings) => {
          this.ngZone.run(() => (this.availableTrainings = trainings));
        });
        this.trainingService.loadTrainings();
      }
    }
  }

  onSportChange(): void {
    this.filteredZoneSystems = this.allZoneSystems.filter((z) => z.sportType === this.selectedSport);
    const defaultSystem = this.filteredZoneSystems.find((z) => z.defaultForSport);
    this.selectedZoneSystemId = defaultSystem?.id ?? '';
  }

  setMode(mode: 'ai' | 'select'): void {
    this.mode = mode;
    this.errorMessage = '';
    this.aiMessage = '';
    this.successMessage = '';
  }

  selectTraining(t: Training): void {
    this.selectedTrainingId = this.selectedTrainingId === t.id ? null : t.id;
  }

  confirmSelection(): void {
    if (!this.selectedTrainingId || this.loading || !this.context.clubId || !this.context.sessionId) return;

    this.loading = true;
    this.errorMessage = '';
    this.aiMessage = '';
    this.successMessage = '';

    this.clubService
      .linkTrainingToSession(this.context.clubId, this.context.sessionId, this.selectedTrainingId)
      .subscribe({
        next: () => {
          this.ngZone.run(() => {
            this.loading = false;
            this.successMessage = 'Training linked to session successfully.';
            this.created.emit({ success: true, content: 'Training linked.' });
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

  submit(): void {
    if (!this.prompt.trim() || this.loading) return;

    this.loading = true;
    this.errorMessage = '';
    this.aiMessage = '';
    this.successMessage = '';

    const ctx: ActionContext = {
      ...this.context,
      clubGroupId: this.selectedGroupId || this.context.clubGroupId,
      sport: this.selectedSport || undefined,
      zoneSystemId: this.selectedZoneSystemId || undefined,
    };

    this.aiActionService.executeAction(this.prompt.trim(), this.actionType, ctx).subscribe({
      next: (result) => {
        this.ngZone.run(() => {
          this.loading = false;
          if (result.success) {
            this.successMessage = result.content;
            this.created.emit(result);
          } else {
            // AI is asking for clarification — show as info, not error
            this.aiMessage = result.content;
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
  }

  close(): void {
    if (this.loading) return;
    this.closed.emit();
  }

  private resetState(): void {
    this.prompt = '';
    this.loading = false;
    this.errorMessage = '';
    this.aiMessage = '';
    this.successMessage = '';
    this.selectedGroupId = '';
    this.availableGroups = [];
    this.selectedSport = '';
    this.selectedZoneSystemId = '';
    this.allZoneSystems = [];
    this.filteredZoneSystems = [];
    this.mode = 'ai';
    this.availableTrainings = [];
    this.selectedTrainingId = null;
    this.searchQuery = '';
  }
}
