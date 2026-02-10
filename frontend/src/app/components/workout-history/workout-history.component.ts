import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HistoryService, SavedSession } from '../../services/history.service';
import { Observable } from 'rxjs';

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="history-container">
      <h3 class="section-title">Completed Sessions</h3>
      <div class="history-list" *ngIf="sessions$ | async as sessions">
        <div class="empty-state" *ngIf="sessions.length === 0">
          <p>No completed workouts yet</p>
        </div>

        <div 
          *ngFor="let session of sessions" 
          class="history-item"
        >
          <div class="item-icon">✓</div>
          <div class="item-details">
            <span class="item-title">{{ session.title }}</span>
            <span class="item-meta">
              {{ formatTime(session.totalDuration) }} • {{ session.avgPower }}W avg
              <span class="sync-icons">
                <span *ngIf="session.syncedToStrava" title="Synced to Strava">🚴</span>
                <span *ngIf="session.syncedToGarmin" title="Synced to Garmin">⌚</span>
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .history-container {
      padding: 12px 0;
    }
    .section-title {
      font-size: 12px;
      text-transform: uppercase;
      color: var(--text-muted);
      letter-spacing: 1px;
      margin-bottom: 16px;
      padding-left: 12px;
    }
    .history-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .empty-state {
      padding: 20px 12px;
      text-align: center;
      color: var(--text-muted);
      font-size: 12px;
    }
    .history-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      border-radius: 12px;
      cursor: pointer;
      transition: background 0.2s;
      border: 1px solid transparent;
    }
    .history-item:hover {
      background: rgba(255, 255, 255, 0.05);
    }
    .item-icon {
      font-size: 20px;
      color: #2ecc71;
    }
    .item-details {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      flex: 1;
    }
    .item-title {
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .item-meta {
      font-size: 11px;
      color: var(--text-muted);
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .sync-icons {
      display: inline-flex;
      gap: 4px;
      margin-left: 4px;
    }
    .sync-icons span {
      font-size: 10px;
    }
  `]
})
export class WorkoutHistoryComponent {
    private historyService = inject(HistoryService);
    sessions$: Observable<SavedSession[]> = this.historyService.sessions$;

    formatTime(seconds: number): string {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        if (h > 0) return `${h}h ${m}m`;
        return `${m} min`;
    }

    formatDate(date: Date): string {
        return new Date(date).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
}
