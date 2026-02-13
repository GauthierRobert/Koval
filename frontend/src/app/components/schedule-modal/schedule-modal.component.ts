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
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()">
      <div class="modal-card glass" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2>{{ mode === 'coach' ? 'ASSIGN' : 'SCHEDULE' }} <span class="highlight">WORKOUT</span></h2>
          <button class="close-btn" (click)="close()">&times;</button>
        </div>

        <div class="modal-body">
          <div class="field">
            <label>Training</label>
            <select [(ngModel)]="selectedTrainingId">
              <option value="" disabled>Select a training...</option>
              <option *ngFor="let t of trainings" [value]="t.id">{{ t.title }}</option>
            </select>
          </div>

          <div class="field">
            <label>Date</label>
            <input type="date" [(ngModel)]="selectedDate" />
          </div>

          <div class="field" *ngIf="mode === 'coach' && availableAthletes.length > 0">
            <label>Athletes</label>
            <div class="tag-filter-row" *ngIf="availableTags.length > 0">
              <span *ngFor="let tag of availableTags"
                    class="modal-tag-chip"
                    [class.active]="isTagActive(tag)"
                    (click)="toggleTag(tag)">{{ tag }}</span>
            </div>
            <div class="athlete-checkboxes">
              <label *ngFor="let a of availableAthletes" class="checkbox-label">
                <input type="checkbox" [checked]="isAthleteSelected(a.id)" (change)="toggleAthlete(a.id)" />
                <span>{{ a.displayName }}</span>
              </label>
            </div>
          </div>

          <div class="field">
            <label>Notes <span class="optional">(optional)</span></label>
            <textarea [(ngModel)]="notes" placeholder="e.g. Focus on cadence during intervals" rows="3"></textarea>
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" (click)="close()">CANCEL</button>
          <button class="submit-btn" (click)="submit()" [disabled]="!canSubmit()">
            {{ mode === 'coach' ? 'ASSIGN' : 'SCHEDULE' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-card {
      width: 440px;
      max-height: 80vh;
      overflow-y: auto;
      background: rgba(22, 22, 28, 0.97);
      border: 1px solid var(--glass-border);
      border-radius: 12px;
      display: flex;
      flex-direction: column;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.2rem 1.5rem;
      border-bottom: 1px solid var(--glass-border);
    }

    .modal-header h2 {
      font-size: 14px;
      font-weight: 800;
      letter-spacing: 2px;
      margin: 0;
    }

    .highlight { color: var(--accent-color); }

    .close-btn {
      background: none;
      border: none;
      color: var(--text-muted);
      font-size: 20px;
      cursor: pointer;
      line-height: 1;
    }

    .modal-body {
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.2rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field label {
      font-size: 10px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--text-muted);
    }

    .optional { font-weight: 400; opacity: 0.6; }

    select, input[type="date"], textarea {
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: 6px;
      padding: 10px 12px;
      color: var(--text-color);
      font-size: 13px;
      font-family: inherit;
      outline: none;
      transition: border-color 0.2s;
    }

    select:focus, input[type="date"]:focus, textarea:focus {
      border-color: var(--accent-color);
    }

    select option {
      background: var(--surface-color);
      color: var(--text-color);
    }

    textarea {
      resize: vertical;
      min-height: 60px;
    }

    .athlete-checkboxes {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 140px;
      overflow-y: auto;
      padding: 8px;
      background: rgba(255, 255, 255, 0.02);
      border-radius: 6px;
      border: 1px solid var(--glass-border);
    }

    .checkbox-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: #ccc;
      cursor: pointer;
      padding: 4px 0;
    }

    .checkbox-label input[type="checkbox"] {
      accent-color: var(--accent-color);
    }

    .tag-filter-row {
      display: flex;
      gap: 4px;
      flex-wrap: wrap;
      margin-bottom: 6px;
    }

    .modal-tag-chip {
      font-size: 9px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #ccc;
      cursor: pointer;
      transition: all 0.2s;
    }

    .modal-tag-chip:hover { background: rgba(255, 157, 0, 0.12); color: var(--accent-color); }
    .modal-tag-chip.active {
      background: rgba(255, 157, 0, 0.2);
      border-color: var(--accent-color);
      color: var(--accent-color);
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--glass-border);
    }

    .cancel-btn {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--glass-border);
      color: var(--text-muted);
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 800;
      cursor: pointer;
      letter-spacing: 0.5px;
    }

    .submit-btn {
      background: var(--accent-color);
      border: none;
      color: #000;
      padding: 8px 20px;
      border-radius: 8px;
      font-size: 11px;
      font-weight: 800;
      cursor: pointer;
      letter-spacing: 0.5px;
      transition: filter 0.2s;
    }

    .submit-btn:hover:not(:disabled) { filter: brightness(1.2); }
    .submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }

    ::-webkit-scrollbar { width: 4px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.05); border-radius: 2px; }
  `]
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
