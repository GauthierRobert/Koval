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

    getMySchedule(start: string, end: string): Observable<ScheduledWorkout[]> {
        return this.http
            .get<ScheduledWorkout[]>(this.apiUrl, {
                params: { start, end },
            })
            .pipe(catchError(() => of([])));
    }

    scheduleWorkout(
        trainingId: string,
        scheduledDate: string,
        notes?: string
    ): Observable<ScheduledWorkout> {
        return this.http.post<ScheduledWorkout>(
            this.apiUrl,
            { trainingId, scheduledDate, notes }
        );
    }

    deleteScheduledWorkout(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
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
