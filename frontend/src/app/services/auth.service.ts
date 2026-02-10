import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { Router } from '@angular/router';

export interface User {
    id: string;
    displayName: string;
    profilePicture: string;
    role: 'ATHLETE' | 'COACH';
    hasCoach: boolean;
    ftp?: number;
    tags?: string[];
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private apiUrl = 'http://localhost:8080/api/auth';
    private userSubject = new BehaviorSubject<User | null>(null);
    user$ = this.userSubject.asObservable();

    constructor(private http: HttpClient, private router: Router) {
        this.loadUserFromToken();
    }

    private loadUserFromToken() {
        const token = localStorage.getItem('token');
        if (token) {
            this.http.get<User>(`${this.apiUrl}/me`, {
                headers: { Authorization: `Bearer ${token}` }
            }).subscribe({
                next: (user) => this.userSubject.next(user),
                error: () => this.provideMockUser()
            });
        } else {
            this.provideMockUser();
        }
    }

    private provideMockUser() {
        console.warn('Backend unreachable or no token, providing mock user session');
        this.userSubject.next({
            id: 'mock-user-123',
            displayName: 'Demo Athlete',
            profilePicture: '',
            role: 'COACH', // Set to COACH so we can see both parts of the app
            hasCoach: false,
            ftp: 250
        });
    }

    getStravaAuthUrl(): Observable<{ authUrl: string }> {
        return this.http.get<{ authUrl: string }>(`${this.apiUrl}/strava`);
    }

    handleStravaCallback(code: string): Observable<{ token: string, user: User }> {
        return this.http.get<{ token: string, user: User }>(`${this.apiUrl}/strava/callback?code=${code}`).pipe(
            tap(response => {
                localStorage.setItem('token', response.token);
                this.userSubject.next(response.user);
            })
        );
    }

    logout() {
        localStorage.removeItem('token');
        this.userSubject.next(null);
        this.router.navigate(['/login']);
    }

    isAuthenticated(): boolean {
        return !!this.userSubject.value;
    }

    isCoach(): boolean {
        return this.userSubject.value?.role === 'COACH';
    }
}
