import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CoachService, ScheduledWorkout } from '../../services/coach.service';
import { AuthService, User } from '../../services/auth.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS } from '../../services/training.service';

@Component({
  selector: 'app-coach-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ScheduleModalComponent],
  template: `
    <div class="coach-container">
      <div class="header glass">
        <div class="brand">
          <span class="highlight">COACH</span> <span class="dim">MANAGEMENT</span>
        </div>
        <div class="coach-stats">
          <div class="stat">
            <span class="label">Roster</span>
            <span class="value">{{ athletes.length }}</span>
          </div>
          <div class="stat">
            <span class="label">Analysis Needed</span>
            <span class="value">4</span>
          </div>
        </div>
      </div>
      <div class="content">
        <div class="athlete-list glass">
          <div class="section-header">
            <h3>ATHLETES</h3>
            <button class="add-btn" (click)="addAthlete()">+ REGISTER</button>
          </div>

          <div class="tag-filters" *ngIf="allTags.length > 0">
            <span *ngFor="let tag of allTags"
                  class="tag-filter-chip"
                  [class.active]="activeTagFilter === tag"
                  (click)="toggleTagFilter(tag)">{{ tag }}</span>
          </div>

          <div class="athletes">
            <div *ngFor="let athlete of filteredAthletes"
                 class="athlete-row"
                 [class.selected]="selectedAthlete?.id === athlete.id"
                 (click)="selectAthlete(athlete)">
              <div class="avatar-sm">{{ athlete.displayName[0].toUpperCase() }}</div>
              <div class="info">
                <span class="name">{{ athlete.displayName }}</span>
                <div class="tag-chips" *ngIf="athlete.tags && athlete.tags.length > 0">
                  <span *ngFor="let tag of athlete.tags" class="chip tag">{{ tag }}</span>
                </div>
                <div class="mini-metrics">
                   <span class="chip fitness">C: 84</span>
                   <span class="chip fatigue">A: 102</span>
                   <span class="chip form positive">F: +12</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="athlete-detail glass" *ngIf="selectedAthlete">
          <div class="detail-header">
            <div class="profile-summary">
              <div class="main-info">
                <h2>{{ selectedAthlete.displayName }}</h2>
                <div class="physiological-tabs">
                   <div class="tab active">PERFORMANCE</div>
                   <div class="tab">PHYSIOLOGY</div>
                   <div class="tab">HISTORY</div>
                </div>
              </div>
              <div class="actions">
                <button class="assign-btn" (click)="assignWorkout()">ASSIGN WORKOUT</button>
              </div>
            </div>

            <div class="detail-tags">
              <span class="detail-tags-label">TAGS</span>
              <div class="detail-tags-row">
                <span *ngFor="let tag of selectedAthlete.tags || []" class="editable-tag">
                  {{ tag }}
                  <button class="tag-remove" (click)="removeTag(selectedAthlete, tag)">&times;</button>
                </span>
                <div class="tag-add-inline">
                  <input
                    class="tag-input"
                    [(ngModel)]="newTagInput"
                    (keydown.enter)="addTag(selectedAthlete)"
                    placeholder="Add tag..."
                    list="tag-suggestions"
                  />
                  <datalist id="tag-suggestions">
                    <option *ngFor="let tag of allTags" [value]="tag"></option>
                  </datalist>
                  <button class="tag-add-btn" (click)="addTag(selectedAthlete)">+</button>
                </div>
              </div>
            </div>

            <div class="metrics-dashboard">
               <div class="m-card">
                 <span class="m-label">FITNESS (CTL)</span>
                 <span class="m-value">84.2</span>
                 <span class="m-trend up">↑ 2.4</span>
               </div>
               <div class="m-card">
                 <span class="m-label">FATIGUE (ATL)</span>
                 <span class="m-value">102.8</span>
                 <span class="m-trend down">↓ 5.1</span>
               </div>
               <div class="m-card">
                 <span class="m-label">FORM (TSB)</span>
                 <span class="m-value">+12.4</span>
                 <span class="m-condition fresh">FRESH</span>
               </div>
               <div class="m-card">
                 <span class="m-label">FTP (EST)</span>
                 <span class="m-value">315W</span>
                 <span class="m-meta">4.2 W/kg</span>
               </div>
            </div>
          </div>
          <div class="technical-schedule">
            <div class="section-title">TECHNICAL SCHEDULE <span class="week-label">WEEK 06</span></div>
            <div class="schedule-table">
              <div class="table-header">
                <span>DATE</span>
                <span>SESSION TITLE</span>
                <span>TYPE</span>
                <span>DUR</span>
                <span>IF</span>
                <span>TSS</span>
                <span>STATUS</span>
              </div>
              <div *ngFor="let workout of athleteSchedule" class="table-row">
                <span class="t-date">{{ workout.scheduledDate | date:'MMM d, EEE' }}</span>
                <span class="t-title">{{ getWorkoutTitle(workout) }}</span>
                <span class="t-type">
                  <span
                    *ngIf="workout.trainingType"
                    class="schedule-type-badge"
                    [style.background]="getTypeColor(workout.trainingType) + '20'"
                    [style.color]="getTypeColor(workout.trainingType)"
                    [style.border-color]="getTypeColor(workout.trainingType) + '40'"
                  >{{ getTypeLabel(workout.trainingType) }}</span>
                  <span *ngIf="!workout.trainingType">-</span>
                </span>
                <span class="t-dur">{{ getWorkoutDuration(workout) }}</span>
                <span class="t-if">{{ workout.intensityFactor || workout.if || '-' }}</span>
                <span class="t-tss">{{ workout.tss || '-' }}</span>
                <span class="t-status" [class.pending]="workout.status === 'PENDING'">{{ workout.status }}</span>
              </div>
              <div *ngIf="athleteSchedule.length === 0" class="table-row empty-row">
                <span class="t-empty">No scheduled workouts for this period</span>
              </div>
            </div>
          </div>
        </div>
        <div class="placeholder-detail glass" *ngIf="!selectedAthlete">
           <div class="empty-detail">
             <span class="icon">&#128269;</span>
             <h3>SELECT ATHLETE FOR DETAILED PHYSIOLOGICAL ANALYSIS</h3>
             <p>Manage training loads and performance progression.</p>
           </div>
        </div>
      </div>
    </div>

    <app-schedule-modal
      [isOpen]="isScheduleModalOpen"
      [preselectedAthletes]="selectedAthlete ? [selectedAthlete] : null"
      mode="coach"
      (closed)="isScheduleModalOpen = false"
      (scheduled)="onScheduled()"
    ></app-schedule-modal>
  `,
  styles: [`
    .coach-container {
      padding: 1.5rem;
      height: 100%;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      background: var(--bg-color);
      color: var(--text-color);
      overflow: hidden;
      font-size: 13px;
    }

    .header {
      padding: 0.8rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-radius: 8px;
    }

    .brand { font-size: 14px; font-weight: 800; letter-spacing: 2px; }
    .dim { opacity: 0.4; font-weight: 400; }
    .highlight { color: var(--accent-color); }

    .coach-stats { display: flex; gap: 2rem; }
    .stat { display: flex; flex-direction: column; align-items: flex-end; }
    .stat .label { font-size: 9px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
    .stat .value { font-size: 16px; font-weight: 700; color: var(--text-color); }

    .content {
      flex: 1;
      display: flex;
      gap: 1rem;
      overflow: hidden;
    }

    .athlete-list {
      width: 280px;
      display: flex;
      flex-direction: column;
      padding: 1rem;
      border-radius: 8px;
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }

    .section-header h3 { font-size: 11px; letter-spacing: 1px; color: var(--text-muted); }

    .add-btn {
      background: rgba(255, 157, 0, 0.08);
      border: 1px solid rgba(255, 157, 0, 0.2);
      color: var(--accent-color);
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 9px;
      font-weight: 800;
      cursor: pointer;
      transition: all 0.2s;
    }

    .add-btn:hover {
      background: rgba(255, 157, 0, 0.15);
    }

    .athletes {
      flex: 1;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .athlete-row {
      padding: 8px 12px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      border: 1px solid transparent;
      transition: background 0.2s;
    }

    .athlete-row:hover { background: rgba(255,255,255,0.03); }
    .athlete-row.selected {
      background: rgba(255,157,0,0.05);
      border-color: rgba(255,157,0,0.2);
    }

    .avatar-sm {
      width: 32px;
      height: 32px;
      background: rgba(255, 157, 0, 0.12);
      color: var(--accent-color);
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: bold;
      font-size: 11px;
    }

    .info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .info .name { font-weight: 700; font-size: 12px; color: var(--text-color); }

    .mini-metrics { display: flex; gap: 4px; }
    .chip { font-size: 8px; font-weight: 800; padding: 1px 4px; border-radius: 2px; }
    .fitness { background: rgba(0, 160, 233, 0.12); color: var(--secondary-color); }
    .fatigue { background: rgba(248, 113, 113, 0.12); color: var(--danger-color); }
    .form { background: rgba(52, 211, 153, 0.12); color: var(--success-color); }
    .tag { background: rgba(255, 157, 0, 0.15); color: var(--accent-color); }

    .tag-chips { display: flex; gap: 3px; flex-wrap: wrap; }

    .tag-filters {
      display: flex;
      gap: 4px;
      flex-wrap: wrap;
      margin-bottom: 8px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--glass-border);
    }

    .tag-filter-chip {
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

    .tag-filter-chip:hover { background: rgba(255, 157, 0, 0.12); color: var(--accent-color); }
    .tag-filter-chip.active {
      background: rgba(255, 157, 0, 0.2);
      border-color: var(--accent-color);
      color: var(--accent-color);
    }

    .detail-tags { display: flex; flex-direction: column; gap: 6px; }
    .detail-tags-label { font-size: 9px; font-weight: 800; color: var(--text-muted); letter-spacing: 1px; }
    .detail-tags-row { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

    .editable-tag {
      font-size: 10px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 10px;
      background: rgba(255, 157, 0, 0.15);
      border: 1px solid rgba(255, 157, 0, 0.3);
      color: var(--accent-color);
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .tag-remove {
      background: none;
      border: none;
      color: var(--accent-color);
      font-size: 12px;
      cursor: pointer;
      padding: 0;
      line-height: 1;
      opacity: 0.6;
    }

    .tag-remove:hover { opacity: 1; }

    .tag-add-inline { display: flex; align-items: center; gap: 4px; }

    .tag-input {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 10px;
      padding: 3px 8px;
      color: #e0e0e0;
      font-size: 10px;
      font-family: inherit;
      width: 100px;
      outline: none;
    }

    .tag-input:focus { border-color: var(--accent-color); }

    .tag-add-btn {
      background: rgba(255, 157, 0, 0.15);
      border: 1px solid rgba(255, 157, 0, 0.3);
      color: var(--accent-color);
      width: 20px;
      height: 20px;
      border-radius: 50%;
      font-size: 12px;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
    }

    .tag-add-btn:hover { background: rgba(255, 157, 0, 0.25); }

    .athlete-detail {
      flex: 1;
      padding: 2rem;
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      gap: 2rem;
      overflow-y: auto;
    }

    .detail-header {
      display: flex;
      flex-direction: column;
      gap: 2rem;
    }

    .profile-summary {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }

    .profile-summary h2 { font-size: 24px; font-weight: 800; letter-spacing: -1px; margin-bottom: 12px; color: var(--text-color); }

    .physiological-tabs { display: flex; gap: 20px; border-bottom: 1px solid var(--glass-border); }
    .tab { font-size: 10px; font-weight: 800; letter-spacing: 1px; padding: 8px 0; color: var(--text-muted); cursor: pointer; }
    .tab.active { color: var(--accent-color); border-bottom: 2px solid var(--accent-color); }

    .assign-btn {
      background: var(--accent-color);
      color: #000;
      border: none;
      padding: 8px 16px;
      border-radius: 8px;
      font-size: 11px;
      font-weight: 800;
      cursor: pointer;
      transition: filter 0.2s;
    }

    .assign-btn:hover {
      filter: brightness(1.15);
    }

    .metrics-dashboard {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
    }

    .m-card {
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      padding: 1.2rem;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .m-label { font-size: 9px; font-weight: 800; color: var(--text-muted); letter-spacing: 1px; }
    .m-value { font-size: 20px; font-weight: 800; color: var(--text-color); }
    .m-trend { font-size: 10px; font-weight: 700; }
    .m-trend.up { color: var(--success-color); }
    .m-trend.down { color: var(--danger-color); }
    .m-meta { font-size: 10px; color: var(--text-muted); }
    .m-condition { font-size: 10px; font-weight: 800; color: var(--secondary-color); }

    .technical-schedule {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .section-title { font-size: 11px; font-weight: 800; letter-spacing: 1px; color: var(--text-muted); display: flex; justify-content: space-between; }
    .week-label { color: var(--accent-color); }

    .schedule-table {
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: 8px;
    }

    .table-header, .table-row {
      display: grid;
      grid-template-columns: 120px 1fr 90px 80px 60px 60px 100px;
      padding: 12px 16px;
      align-items: center;
    }

    .table-header {
      font-size: 9px;
      font-weight: 800;
      color: var(--text-muted);
      border-bottom: 1px solid var(--glass-border);
    }

    .table-row {
      font-size: 12px;
      border-bottom: 1px solid rgba(255,255,255,0.03);
    }

    .table-row:last-child { border: none; }
    .t-date { font-weight: 700; color: var(--text-muted); }
    .t-title { font-weight: 700; color: var(--text-color); }
    .t-status { font-size: 9px; font-weight: 800; text-transform: uppercase; }
    .t-status.pending { color: var(--accent-color); }

    .schedule-type-badge {
      font-size: 8px;
      font-weight: 800;
      padding: 2px 6px;
      border-radius: 6px;
      border: 1px solid;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      white-space: nowrap;
    }

    .empty-row { grid-template-columns: 1fr; }
    .t-empty { color: var(--text-muted); font-size: 11px; text-align: center; }

    .placeholder-detail {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
    }

    .empty-detail { text-align: center; color: var(--text-muted); max-width: 300px; }
    .empty-detail .icon { font-size: 3rem; display: block; margin-bottom: 1.5rem; opacity: 0.3; }
    .empty-detail h3 { font-size: 12px; font-weight: 800; letter-spacing: 1px; color: var(--text-color); margin-bottom: 8px; }
    .empty-detail p { font-size: 11px; line-height: 1.6; }

    ::-webkit-scrollbar { width: 4px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.05); border-radius: 2px; }
  `]
})
export class CoachDashboardComponent implements OnInit {
  athletes: User[] = [];
  selectedAthlete: User | null = null;
  athleteSchedule: ScheduledWorkout[] = [];
  isScheduleModalOpen = false;
  allTags: string[] = [];
  activeTagFilter: string | null = null;
  newTagInput = '';

  private userId = '';

  constructor(
    private coachService: CoachService,
    private authService: AuthService
  ) { }

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      if (u) {
        this.userId = u.id;
        this.loadAthletes();
        this.loadTags();
      }
    });
  }

  loadAthletes() {
    this.coachService.getAthletes(this.userId).subscribe({
      next: (data) => this.athletes = data,
      error: (err) => console.error('Error loading athletes', err)
    });
  }

  loadTags() {
    this.coachService.getAllTags(this.userId).subscribe({
      next: (tags) => this.allTags = tags,
      error: (err) => console.error('Error loading tags', err)
    });
  }

  get filteredAthletes(): User[] {
    if (!this.activeTagFilter) return this.athletes;
    return this.athletes.filter(a => a.tags?.includes(this.activeTagFilter!));
  }

  toggleTagFilter(tag: string) {
    this.activeTagFilter = this.activeTagFilter === tag ? null : tag;
  }

  selectAthlete(athlete: User) {
    this.selectedAthlete = athlete;
    this.loadAthleteSchedule(athlete.id);
  }

  loadAthleteSchedule(athleteId: string) {
    const now = new Date();
    const start = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];
    const end = new Date(now.getTime() + (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];

    this.coachService.getAthleteSchedule(this.userId, athleteId, start, end).subscribe({
      next: (data) => this.athleteSchedule = data,
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
    this.coachService.addAthleteTag(this.userId, athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.tags = updated.tags;
        this.newTagInput = '';
        this.loadTags();
      },
      error: () => {
        // Fallback for mock mode
        if (!athlete.tags) athlete.tags = [];
        if (!athlete.tags.includes(tag)) athlete.tags.push(tag);
        this.newTagInput = '';
        this.refreshLocalTags();
      }
    });
  }

  removeTag(athlete: User | null, tag: string) {
    if (!athlete) return;
    this.coachService.removeAthleteTag(this.userId, athlete.id, tag).subscribe({
      next: (updated) => {
        athlete.tags = updated.tags;
        this.loadTags();
      },
      error: () => {
        // Fallback for mock mode
        if (athlete.tags) {
          athlete.tags = athlete.tags.filter(t => t !== tag);
        }
        this.refreshLocalTags();
      }
    });
  }

  private refreshLocalTags() {
    const tagSet = new Set<string>();
    this.athletes.forEach(a => a.tags?.forEach(t => tagSet.add(t)));
    this.allTags = [...tagSet].sort();
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  addAthlete() {
    alert('Search for athletes to add them to your roster.');
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
