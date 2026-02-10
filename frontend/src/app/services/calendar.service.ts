import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ScheduledWorkout } from './coach.service';

@Injectable({
    providedIn: 'root',
})
export class CalendarService {
    private apiUrl = 'http://localhost:8080/api/schedule';

    constructor(private http: HttpClient) {}

    private getUserId(): string {
        // Sync read from auth — same pattern used by TrainingService
        const stored = localStorage.getItem('token');
        // For now, use the mock user id when no real auth
        return 'mock-user-123';
    }

    private getHeaders(userId?: string) {
        return { 'X-User-Id': userId || this.getUserId() };
    }

    getMySchedule(userId: string, start: string, end: string): Observable<ScheduledWorkout[]> {
        return this.http
            .get<ScheduledWorkout[]>(this.apiUrl, {
                headers: { 'X-User-Id': userId },
                params: { start, end },
            })
            .pipe(catchError(() => of([])));
    }

    scheduleWorkout(
        userId: string,
        trainingId: string,
        scheduledDate: string,
        notes?: string
    ): Observable<ScheduledWorkout> {
        return this.http.post<ScheduledWorkout>(
            this.apiUrl,
            { trainingId, scheduledDate, notes },
            { headers: { 'X-User-Id': userId } }
        );
    }

    deleteScheduledWorkout(userId: string, id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`, {
            headers: { 'X-User-Id': userId },
        });
    }

    markCompleted(scheduledWorkoutId: string): Observable<ScheduledWorkout> {
        return this.http.post<ScheduledWorkout>(
            `${this.apiUrl}/${scheduledWorkoutId}/complete`,
            {}
        );
    }

    markSkipped(scheduledWorkoutId: string): Observable<ScheduledWorkout> {
        return this.http.post<ScheduledWorkout>(
            `${this.apiUrl}/${scheduledWorkoutId}/skip`,
            {}
        );
    }
}
