import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { User } from './auth.service';

export interface ScheduledWorkout {
    id: string;
    trainingId: string;
    athleteId: string;
    assignedBy: string;
    scheduledDate: string;
    status: 'PENDING' | 'COMPLETED' | 'SKIPPED';
    notes?: string;
    tss?: number;
    intensityFactor?: number;
    completedAt?: string;
    createdAt?: string;
    // Enriched fields from backend
    trainingTitle?: string;
    totalDurationSeconds?: number;
    // Legacy display fields (kept for compatibility)
    title?: string;
    duration?: string;
    if?: number;
}

const MOCK_ATHLETES: User[] = [
    { id: 'ath-1', displayName: 'Thomas Pidcock', profilePicture: '', role: 'ATHLETE', hasCoach: true, tags: ['Club BTC', 'Junior'] },
    { id: 'ath-2', displayName: 'Mathieu van der Poel', profilePicture: '', role: 'ATHLETE', hasCoach: true, tags: ['Club BTC', 'Triathlon'] },
    { id: 'ath-3', displayName: 'Wout van Aert', profilePicture: '', role: 'ATHLETE', hasCoach: true, tags: ['Triathlon'] },
];

@Injectable({
    providedIn: 'root',
})
export class CoachService {
    private apiUrl = 'http://localhost:8080/api/coach';

    constructor(private http: HttpClient) {}

    getAthletes(userId: string): Observable<User[]> {
        return this.http
            .get<User[]>(`${this.apiUrl}/athletes`, {
                headers: { 'X-User-Id': userId },
            })
            .pipe(catchError(() => of(MOCK_ATHLETES)));
    }

    getAthleteSchedule(
        userId: string,
        athleteId: string,
        start: string,
        end: string
    ): Observable<ScheduledWorkout[]> {
        return this.http
            .get<ScheduledWorkout[]>(`${this.apiUrl}/schedule/${athleteId}`, {
                headers: { 'X-User-Id': userId },
                params: { start, end },
            })
            .pipe(catchError(() => of([])));
    }

    assignTraining(
        userId: string,
        trainingId: string,
        athleteIds: string[],
        date: string,
        notes?: string
    ): Observable<ScheduledWorkout[]> {
        return this.http.post<ScheduledWorkout[]>(
            `${this.apiUrl}/assign`,
            { trainingId, athleteIds, scheduledDate: date, notes },
            { headers: { 'X-User-Id': userId } }
        );
    }

    updateAthleteTags(userId: string, athleteId: string, tags: string[]): Observable<User> {
        return this.http.put<User>(`${this.apiUrl}/athletes/${athleteId}/tags`, { tags }, {
            headers: { 'X-User-Id': userId },
        });
    }

    addAthleteTag(userId: string, athleteId: string, tag: string): Observable<User> {
        return this.http.post<User>(`${this.apiUrl}/athletes/${athleteId}/tags`, { tag }, {
            headers: { 'X-User-Id': userId },
        });
    }

    removeAthleteTag(userId: string, athleteId: string, tag: string): Observable<User> {
        return this.http.delete<User>(`${this.apiUrl}/athletes/${athleteId}/tags/${encodeURIComponent(tag)}`, {
            headers: { 'X-User-Id': userId },
        });
    }

    getAllTags(userId: string): Observable<string[]> {
        return this.http
            .get<string[]>(`${this.apiUrl}/athletes/tags`, {
                headers: { 'X-User-Id': userId },
            })
            .pipe(
                catchError(() => {
                    const allTags = MOCK_ATHLETES.flatMap(a => a.tags || []);
                    return of([...new Set(allTags)].sort());
                })
            );
    }
}
