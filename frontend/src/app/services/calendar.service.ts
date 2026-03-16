import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {ScheduledWorkout} from './coach.service';
import {SavedSession} from './history.service';
import {environment} from '../../environments/environment';

export interface CalendarClubSession {
    id: string;
    clubId: string;
    clubName: string;
    title: string;
    sport?: string;
    scheduledAt: string;
    location?: string;
    description?: string;
    durationMinutes?: number;
    participantIds: string[];
    maxParticipants?: number;
    clubGroupId?: string;
    clubGroupName?: string;
    joined: boolean;
    onWaitingList: boolean;
    waitingListPosition: number;
    openToAllFrom?: string;
}

const BASE = environment.apiUrl;

@Injectable({
    providedIn: 'root',
})
export class CalendarService {
    private apiUrl = `${BASE}/api/schedule`;

    constructor(private http: HttpClient) { }

    getMySchedule(start: string, end: string, includeClubSessions = false): Observable<ScheduledWorkout[]> {
        const params: Record<string, string> = { start, end };
        if (includeClubSessions) params['includeClubSessions'] = 'true';
        return this.http
            .get<ScheduledWorkout[]>(this.apiUrl, { params })
            .pipe(catchError(() => of([] as ScheduledWorkout[])));
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
            catchError(() => of([] as SavedSession[]))
        );
    }

    linkSessionToSchedule(sessionId: string, scheduledWorkoutId: string): Observable<any> {
        return this.http.post(`${BASE}/api/sessions/${sessionId}/link/${scheduledWorkoutId}`, {});
    }

    getClubSessionsForCalendar(start: string, end: string): Observable<CalendarClubSession[]> {
        return this.http
            .get<CalendarClubSession[]>(`${this.apiUrl}/club-sessions`, {
                params: { start, end },
            })
            .pipe(catchError(() => of([] as CalendarClubSession[])));
    }
}
