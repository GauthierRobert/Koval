import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {ScheduledWorkout} from './coach.service';
import {SavedSession} from './history.service';

const BASE = 'http://localhost:8080';

@Injectable({
    providedIn: 'root',
})
export class CalendarService {
    private apiUrl = `${BASE}/api/schedule`;

    constructor(private http: HttpClient) { }

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

    rescheduleWorkout(id: string, newDate: string): Observable<ScheduledWorkout> {
        return this.http.patch<ScheduledWorkout>(
            `${this.apiUrl}/${id}/reschedule`,
            { scheduledDate: newDate }
        );
    }

    getSessionsForCalendar(start: string, end: string): Observable<SavedSession[]> {
        return this.http.get<any[]>(`${BASE}/api/sessions/calendar`, { params: { start, end } }).pipe(
            map(ss => ss.map(s => ({
                id: s.id,
                title: s.title,
                totalDuration: s.totalDurationSeconds,
                avgPower: s.avgPower,
                avgHR: s.avgHR,
                avgCadence: s.avgCadence,
                avgSpeed: s.avgSpeed ?? 0,
                blockSummaries: s.blockSummaries ?? [],
                history: [],
                sportType: s.sportType,
                date: new Date(s.completedAt),
                syncedToStrava: false,
                syncedToGarmin: false,
                tss: s.tss ?? undefined,
                intensityFactor: s.intensityFactor ?? undefined,
                fitFileId: s.fitFileId ?? undefined,
                scheduledWorkoutId: s.scheduledWorkoutId ?? undefined,
            } as SavedSession))),
            catchError(() => of([]))
        );
    }

    linkSessionToSchedule(sessionId: string, scheduledWorkoutId: string): Observable<any> {
        return this.http.post(`${BASE}/api/sessions/${sessionId}/link/${scheduledWorkoutId}`, {});
    }
}
