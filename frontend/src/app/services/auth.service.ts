import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {Router} from '@angular/router';
import {environment} from '../../environments/environment';
import {NotificationService} from './notification.service';

export interface User {
    id: string;
    displayName: string;
    profilePicture: string;
    role: 'ATHLETE' | 'COACH';
    hasCoach: boolean;
    ftp?: number;
    weightKg?: number;
    functionalThresholdPace?: number;
    criticalSwimSpeed?: number;
    pace5k?: number;
    pace10k?: number;
    paceHalfMarathon?: number;
    paceMarathon?: number;
    vo2maxPower?: number;
    vo2maxPace?: number;
    groups?: string[];
    clubs?: string[];
    ctl?: number;
    atl?: number;
    tsb?: number;
    needsOnboarding?: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private apiUrl = `${environment.apiUrl}/api/auth`;
    private userSubject = new BehaviorSubject<User | null>(null);
    user$ = this.userSubject.asObservable();

    private readonly UI_MODE_KEY = 'uiMode';
    private uiModeSubject = new BehaviorSubject<'athlete' | 'coach'>(
        (localStorage.getItem('uiMode') as 'athlete' | 'coach') ?? 'coach'
    );
    uiMode$ = this.uiModeSubject.asObservable();

    private showSettingsSubject = new BehaviorSubject<boolean>(false);
    showSettings$ = this.showSettingsSubject.asObservable();

    private readonly http = inject(HttpClient);
    private readonly router = inject(Router);
    private readonly notificationService = inject(NotificationService);

    constructor() {
        this.loadUserFromToken();
    }

    private loadUserFromToken() {
        const token = localStorage.getItem('token');
        if (token) {
            this.http.get<User>(`${this.apiUrl}/me`, {
                headers: { Authorization: `Bearer ${token}` }
            }).subscribe({
                next: (user) => {
                    this.userSubject.next(user);
                    this.notificationService.requestPermissionAndRegisterToken();
                },
                error: () => {
                    localStorage.removeItem('token');
                    this.router.navigate(['/login']);
                }
            });
        }
    }

    setUiMode(mode: 'athlete' | 'coach'): void {
        localStorage.setItem(this.UI_MODE_KEY, mode);
        this.uiModeSubject.next(mode);
    }

    // --- Strava OAuth ---

    getStravaAuthUrl(): Observable<{ authUrl: string }> {
        return this.http.get<{ authUrl: string }>(`${this.apiUrl}/strava`);
    }

    handleStravaCallback(code: string): Observable<{ token: string, user: User }> {
        return this.http.get<{ token: string, user: User }>(`${this.apiUrl}/strava/callback?code=${code}`).pipe(
            tap(response => {
                localStorage.setItem('token', response.token);
                this.userSubject.next(response.user);
                this.setUiMode(response.user.role === 'COACH' ? 'coach' : 'athlete');
            })
        );
    }

    // --- Google OAuth ---

    getGoogleAuthUrl(): Observable<{ authUrl: string }> {
        return this.http.get<{ authUrl: string }>(`${this.apiUrl}/google`);
    }

    handleGoogleCallback(code: string): Observable<{ token: string, user: User }> {
        return this.http.get<{ token: string, user: User }>(`${this.apiUrl}/google/callback?code=${code}`).pipe(
            tap(response => {
                localStorage.setItem('token', response.token);
                this.userSubject.next(response.user);
                this.setUiMode(response.user.role === 'COACH' ? 'coach' : 'athlete');
            })
        );
    }

    // --- DEV ONLY — login with arbitrary userId ---

    devLogin(userId: string, displayName?: string, role?: 'ATHLETE' | 'COACH'): Observable<{ token: string, user: User }> {
        return this.http.post<{ token: string, user: User }>(`${this.apiUrl}/dev/login`, {
            userId, displayName, role
        }).pipe(
            tap(response => {
                localStorage.setItem('token', response.token);
                this.userSubject.next(response.user);
                this.setUiMode(response.user.role === 'COACH' ? 'coach' : 'athlete');
            })
        );
    }

    // --- Session management ---

    logout() {
        this.notificationService.unregisterToken();
        localStorage.removeItem('token');
        localStorage.removeItem(this.UI_MODE_KEY);
        window.location.href = '/login';
    }

    get currentUser(): User | null {
        return this.userSubject.value;
    }

    isAuthenticated(): boolean {
        return !!this.userSubject.value;
    }

    isCoach(): boolean {
        return this.userSubject.value?.role === 'COACH';
    }

    toggleSettings(show?: boolean): void {
        if (show !== undefined) {
            this.showSettingsSubject.next(show);
        } else {
            this.showSettingsSubject.next(!this.showSettingsSubject.value);
        }
    }

    updateUser(user: User): void {
        this.userSubject.next(user);
    }

    updateSettings(settings: Partial<User>): Observable<any> {
        const token = localStorage.getItem('token');
        return this.http.put<any>(`${this.apiUrl}/settings`, settings, {
            headers: { Authorization: `Bearer ${token}` }
        }).pipe(
            tap(user => this.userSubject.next(user))
        );
    }

    refreshUser(): void {
        const token = localStorage.getItem('token');
        if (token) {
            this.http.get<User>(`${this.apiUrl}/me`, {
                headers: { Authorization: `Bearer ${token}` }
            }).subscribe({
                next: (user) => this.userSubject.next(user),
            });
        }
    }

    completeOnboarding(data: { role: 'ATHLETE' | 'COACH'; ftp?: number; weightKg?: number; criticalSwimSpeed?: number; functionalThresholdPace?: number }): Observable<{ token: string; user: User }> {
        return this.http.post<{ token: string; user: User }>(`${this.apiUrl}/onboarding`, data).pipe(
            tap(response => {
                localStorage.setItem('token', response.token);
                this.userSubject.next(response.user);
                this.setUiMode(response.user.role === 'COACH' ? 'coach' : 'athlete');
            })
        );
    }
}
