import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {User} from './auth.service';
import {Group} from './group.service';
import {PmcDataPoint} from './metrics.service';
import {environment} from '../../environments/environment';

export interface InviteCode {
    id: string;
    code: string;
    coachId: string;
    groupIds: string[];
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
    sportType: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM';
    sessionId?: string;
    isClubSession?: boolean;
    clubName?: string;
    clubGroupName?: string;
}

@Injectable({
    providedIn: 'root',
})
export class CoachService {
    private apiUrl = `${environment.apiUrl}/api/coach`;

    private errorSubject = new BehaviorSubject<string | null>(null);
    error$ = this.errorSubject.asObservable();

    constructor(private http: HttpClient) { }

    getAthletes(): Observable<User[]> {
        this.errorSubject.next(null);
        return this.http.get<User[]>(`${this.apiUrl}/athletes`).pipe(
            catchError(() => {
                this.errorSubject.next('Failed to load athletes');
                return of([] as User[]);
            })
        );
    }

    getAthleteSchedule(
        athleteId: string,
        start: string,
        end: string
    ): Observable<ScheduledWorkout[]> {
        this.errorSubject.next(null);
        return this.http
            .get<ScheduledWorkout[]>(`${this.apiUrl}/schedule/${athleteId}`, {
                params: { start, end },
            })
            .pipe(catchError(() => {
                this.errorSubject.next('Failed to load athlete schedule');
                return of([] as ScheduledWorkout[]);
            }));
    }

    assignTraining(
        trainingId: string,
        athleteIds: string[],
        date: string,
        notes?: string,
        clubId?: string,
        groupId?: string
    ): Observable<ScheduledWorkout[]> {
        return this.http.post<ScheduledWorkout[]>(
            `${this.apiUrl}/assign`,
            { trainingId, athleteIds, scheduledDate: date, notes, clubId, groupId }
        );
    }

    updateAthleteGroups(athleteId: string, groupIds: string[]): Observable<User> {
        return this.http.put<User>(`${this.apiUrl}/athletes/${athleteId}/groups`, { groups: groupIds });
    }

    addAthleteGroup(athleteId: string, groupName: string): Observable<User> {
        return this.http.post<User>(`${this.apiUrl}/athletes/${athleteId}/groups`, { group: groupName });
    }

    removeAthleteGroup(athleteId: string, groupName: string): Observable<User> {
        return this.http.delete<User>(`${this.apiUrl}/athletes/${athleteId}/groups/${encodeURIComponent(groupName)}`);
    }

    getAllGroups(): Observable<Group[]> {
        return this.http
            .get<Group[]>(`${this.apiUrl}/athletes/groups`)
            .pipe(catchError(() => {
                this.errorSubject.next('Failed to load groups');
                return of([] as Group[]);
            }));
    }

    generateInviteCode(groupIds: string[], maxUses: number, code?: string): Observable<InviteCode> {
        return this.http.post<InviteCode>(
            `${this.apiUrl}/invite-codes`,
            { groups: groupIds, maxUses, code }
        );
    }

    getInviteCodes(): Observable<InviteCode[]> {
        return this.http
            .get<InviteCode[]>(`${this.apiUrl}/invite-codes`)
            .pipe(catchError(() => {
                this.errorSubject.next('Failed to load invite codes');
                return of([] as InviteCode[]);
            }));
    }

    deactivateInviteCode(codeId: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/invite-codes/${codeId}`);
    }

    redeemInviteCode(code: string): Observable<any> {
        return this.http.post<any>(
            `${this.apiUrl}/redeem-invite`,
            { code }
        );
    }

    getSessionReminders(): Observable<any[]> {
        return this.http.get<any[]>(`${this.apiUrl}/session-reminders`).pipe(
            catchError(() => of([] as any[]))
        );
    }

    getAthleteSessions(athleteId: string): Observable<any[]> {
        return this.http
            .get<any[]>(`${this.apiUrl}/athletes/${athleteId}/sessions`)
            .pipe(
                catchError(() => {
                    this.errorSubject.next('Failed to load athlete sessions');
                    return of([] as any[]);
                }),
            );
    }

    getAthletePmc(athleteId: string, from: string, to: string): Observable<PmcDataPoint[]> {
        return this.http
            .get<PmcDataPoint[]>(`${this.apiUrl}/athletes/${athleteId}/pmc`, {
                params: { from, to },
            })
            .pipe(catchError(() => {
                this.errorSubject.next('Failed to load athlete PMC data');
                return of([] as PmcDataPoint[]);
            }));
    }
}
