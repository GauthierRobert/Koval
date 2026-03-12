import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  inject,
  NgZone,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AIActionService, AIActionType, ActionContext, ActionResult } from '../../../services/ai-action.service';
import { ClubService, ClubGroup } from '../../../services/club.service';
import { ZoneService } from '../../../services/zone.service';
import { ZoneSystem } from '../../../services/zone';

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
  private ngZone = inject(NgZone);

  prompt = '';
  loading = false;
  errorMessage = '';
  successMessage = '';

  availableGroups: ClubGroup[] = [];
  selectedGroupId = '';

  selectedSport = '';
  selectedZoneSystemId = '';
  allZoneSystems: ZoneSystem[] = [];
  filteredZoneSystems: ZoneSystem[] = [];
  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING'];

  get showSportSelector(): boolean {
    return this.actionType === 'TRAINING_FROM_NOTATION' || this.actionType === 'TRAINING_WITH_SESSION';
  }

  get showTagSelector(): boolean {
    return (this.actionType === 'TRAINING_WITH_SESSION' || this.actionType === 'TRAINING_FROM_NOTATION')
        && !!this.context.clubId;
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
    }
  }

  onSportChange(): void {
    this.filteredZoneSystems = this.allZoneSystems.filter((z) => z.sportType === this.selectedSport);
    const defaultSystem = this.filteredZoneSystems.find((z) => z.defaultForSport);
    this.selectedZoneSystemId = defaultSystem?.id ?? '';
  }

  submit(): void {
    if (!this.prompt.trim() || this.loading) return;

    this.loading = true;
    this.errorMessage = '';
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
  }

  close(): void {
    if (this.loading) return;
    this.closed.emit();
  }

  private resetState(): void {
    this.prompt = '';
    this.loading = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.selectedGroupId = '';
    this.availableGroups = [];
    this.selectedSport = '';
    this.selectedZoneSystemId = '';
    this.allZoneSystems = [];
    this.filteredZoneSystems = [];
  }
}
