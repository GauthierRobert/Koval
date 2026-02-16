import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { HistoryService, SavedSession } from '../../services/history.service';
import { TrainingService } from '../../services/training.service';
import { Observable } from 'rxjs';

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule, SportIconComponent],
    templateUrl: './workout-history.component.html',
    styleUrl: './workout-history.component.css'
})
export class WorkoutHistoryComponent {
    private historyService = inject(HistoryService);
    private trainingService = inject(TrainingService);
    sessions$: Observable<SavedSession[]> = this.historyService.sessions$;
    selectedSession$ = this.historyService.selectedSession$;

    onSelect(session: SavedSession) {
        this.trainingService.selectTraining(null);
        this.historyService.selectSession(session);
    }

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

    getSportUnit(session: SavedSession): string {
        if (session.sportType === 'RUNNING') return '/km';
        if (session.sportType === 'SWIMMING') return '/100m';
        return 'W';
    }
}
