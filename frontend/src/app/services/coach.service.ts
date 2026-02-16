import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { User } from './auth.service';
import { Tag } from './tag.service';

export interface InviteCode {
    id: string;
    code: string;
    coachId: string;
    tags: string[];
    maxUses: number;
    currentUses: number;
    expiresAt?: string;
    active: boolean;
    createdAt: string;
}

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
    trainingTitle?: string;
    trainingType?: string;
    totalDurationSeconds?: number;
    title?: string;
    duration?: string;
    if?: number;
    sportType?: 'CYCLING' | 'RUNNING' | 'SWIMMING';
}

@Injectable({
    providedIn: 'root',
})
export class CoachService {
    private apiUrl = 'http://localhost:8080/api/coach';

    constructor(private http: HttpClient) { }

    getAthletes(): Observable<User[]> {
        return this.http.get<User[]>(`${this.apiUrl}/athletes`).pipe(
            catchError(() => of([]))
        );
    }

    getAthleteSchedule(
        athleteId: string,
        start: string,
        end: string
    ): Observable<ScheduledWorkout[]> {
        return this.http
            .get<ScheduledWorkout[]>(`${this.apiUrl}/schedule/${athleteId}`, {
                params: { start, end },
            })
            .pipe(catchError(() => of([])));
    }

    assignTraining(
        trainingId: string,
        athleteIds: string[],
        date: string,
        notes?: string
    ): Observable<ScheduledWorkout[]> {
        return this.http.post<ScheduledWorkout[]>(
            `${this.apiUrl}/assign`,
            { trainingId, athleteIds, scheduledDate: date, notes }
        );
    }

    updateAthleteTags(athleteId: string, tags: string[]): Observable<User> {
        return this.http.put<User>(`${this.apiUrl}/athletes/${athleteId}/tags`, { tags });
    }

    addAthleteTag(athleteId: string, tag: string): Observable<User> {
        return this.http.post<User>(`${this.apiUrl}/athletes/${athleteId}/tags`, { tag });
    }

    removeAthleteTag(athleteId: string, tag: string): Observable<User> {
        return this.http.delete<User>(`${this.apiUrl}/athletes/${athleteId}/tags/${encodeURIComponent(tag)}`);
    }

    getAllTags(): Observable<Tag[]> {
        return this.http
            .get<Tag[]>(`${this.apiUrl}/athletes/tags`)
            .pipe(catchError(() => of([])));
    }

    generateInviteCode(tags: string[], maxUses: number): Observable<InviteCode> {
        return this.http.post<InviteCode>(
            `${this.apiUrl}/invite-codes`,
            { tags, maxUses }
        );
    }

    getInviteCodes(): Observable<InviteCode[]> {
        return this.http
            .get<InviteCode[]>(`${this.apiUrl}/invite-codes`)
            .pipe(catchError(() => of([])));
    }

    deactivateInviteCode(codeId: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/invite-codes/${codeId}`);
    }

    redeemInviteCode(code: string): Observable<User> {
        return this.http.post<User>(
            `${this.apiUrl}/redeem-invite`,
            { code }
        );
    }
}
