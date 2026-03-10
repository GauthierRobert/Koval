import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {User} from './auth.service';
import {Tag} from './tag.service';
import {PmcDataPoint} from './metrics.service';
import {environment} from '../../environments/environment';

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
    sportType: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM';
    sessionId?: string;
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
            .pipe(catchError(() => {
                this.errorSubject.next('Failed to load tags');
                return of([] as Tag[]);
            }));
    }

    generateInviteCode(tags: string[], maxUses: number, code?: string): Observable<InviteCode> {
        return this.http.post<InviteCode>(
            `${this.apiUrl}/invite-codes`,
            { tags, maxUses, code }
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

    redeemInviteCode(code: string): Observable<User> {
        return this.http.post<User>(
            `${this.apiUrl}/redeem-invite`,
            { code }
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
