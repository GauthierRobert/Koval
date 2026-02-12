import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { SessionSummary } from './workout-execution.service';

export interface SavedSession extends SessionSummary {
    id: string;
    date: Date;
    syncedToStrava: boolean;
    syncedToGarmin: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class HistoryService {
    private sessionsSubject = new BehaviorSubject<SavedSession[]>([]);
    sessions$ = this.sessionsSubject.asObservable();

    private selectedSessionSubject = new BehaviorSubject<SavedSession | null>(null);
    selectedSession$ = this.selectedSessionSubject.asObservable();

    selectSession(session: SavedSession | null) {
        this.selectedSessionSubject.next(session);
    }

    saveSession(summary: SessionSummary): SavedSession {
        const savedSession: SavedSession = {
            ...summary,
            id: this.generateId(),
            date: new Date(),
            syncedToStrava: true,  // Simulated sync
            syncedToGarmin: true   // Simulated sync
        };

        const currentSessions = this.sessionsSubject.value;
        this.sessionsSubject.next([savedSession, ...currentSessions]);

        return savedSession;
    }

    private generateId(): string {
        return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    deleteSession(id: string) {
        const currentSessions = this.sessionsSubject.value;
        this.sessionsSubject.next(currentSessions.filter(s => s.id !== id));
    }
}
