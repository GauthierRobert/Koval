import {DestroyRef, inject, Injectable} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {filter, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {AuthService} from './auth.service';
import {ReceivedTraining, Training} from '../models/training.model';

// Re-export all model types so existing imports from this file continue to work.
export * from '../models/training.model';

@Injectable({
    providedIn: 'root',
})
export class TrainingService {
    private apiUrl = `${environment.apiUrl}/api/trainings`;
    private http = inject(HttpClient);

    private trainingsSubject = new BehaviorSubject<Training[]>([]);
    trainings$ = this.trainingsSubject.asObservable();

    private selectedTrainingSubject = new BehaviorSubject<Training | null>(null);
    selectedTraining$ = this.selectedTrainingSubject.asObservable();

    private receivedTrainingsSubject = new BehaviorSubject<ReceivedTraining[]>([]);
    receivedTrainings$ = this.receivedTrainingsSubject.asObservable();

    private static readonly FTP_STORAGE_KEY = 'koval_ftp';

    private ftpSubject = new BehaviorSubject<number | null>(this.loadFtp());
    ftp$ = this.ftpSubject.asObservable();

    private authService = inject(AuthService);
    private destroyRef = inject(DestroyRef);

    constructor() {
        this.authService.user$.pipe(
            filter(user => !!user),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(() => {
            this.selectedTrainingSubject.next(null);
            this.loadTrainings();
        });
    }

    get currentFtp(): number | null {
        return this.ftpSubject.value;
    }

    private loadFtp(): number | null {
        const stored = localStorage.getItem(TrainingService.FTP_STORAGE_KEY);
        if (stored) {
            const parsed = parseInt(stored, 10);
            if (!isNaN(parsed) && parsed > 0) return parsed;
        }
        return null;
    }

    loadTrainings(): void {
        this.http.get<Training[]>(this.apiUrl).subscribe({
            next: (trainings) => {
                this.trainingsSubject.next(trainings);
                if (!this.selectedTrainingSubject.value && trainings?.length > 0) {
                    this.selectedTrainingSubject.next(trainings[0]);
                }
            },
            error: () => {
                this.trainingsSubject.next([]);
            },
        });
    }

    private receivedTrainingCache = new Map<string, Training>();

    getTrainingById(id: string): Observable<Training> {
        const cached = this.trainingsSubject.value.find((t) => t.id === id);
        if (cached) return of(cached);
        const receivedCached = this.receivedTrainingCache.get(id);
        if (receivedCached) return of(receivedCached);
        return this.http.get<Training>(`${this.apiUrl}/${id}`).pipe(
            tap((t) => this.receivedTrainingCache.set(t.id, t)),
        );
    }

    createTraining(training: Partial<Training>): Observable<Training> {
        return this.http.post<Training>(this.apiUrl, training).pipe(
            tap(newTraining => {
                const current = this.trainingsSubject.value;
                this.trainingsSubject.next([...current, newTraining]);
            })
        );
    }

    updateTraining(id: string, training: Partial<Training>): Observable<Training> {
        return this.http.put<Training>(`${this.apiUrl}/${id}`, training).pipe(
            tap(updated => {
                const current = this.trainingsSubject.value;
                const index = current.findIndex(t => t.id === id);
                if (index !== -1) {
                    current[index] = updated;
                    this.trainingsSubject.next([...current]);
                }
                if (this.selectedTrainingSubject.value?.id === id) {
                    this.selectedTrainingSubject.next(updated);
                }
            })
        );
    }

    selectTraining(training: Training | null): void {
        this.selectedTrainingSubject.next(training);
    }

    setFtp(ftp: number): void {
        this.ftpSubject.next(ftp);
        localStorage.setItem(TrainingService.FTP_STORAGE_KEY, String(ftp));
    }

    deleteTraining(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
            tap(() => this.removeTrainingLocally(id))
        );
    }

    duplicateTraining(id: string): Observable<Training> {
        return this.http.post<Training>(`${this.apiUrl}/${id}/duplicate`, {}).pipe(
            tap(copy => {
                const current = this.trainingsSubject.value;
                this.trainingsSubject.next([...current, copy]);
            })
        );
    }

    removeTrainingLocally(id: string): void {
        const current = this.trainingsSubject.value.filter((t) => t.id !== id);
        this.trainingsSubject.next(current);
        if (this.selectedTrainingSubject.value?.id === id) {
            this.selectedTrainingSubject.next(current.length > 0 ? current[0] : null);
        }
    }

    loadReceivedTrainings(): void {
        this.http.get<ReceivedTraining[]>(`${this.apiUrl}/received`).subscribe({
            next: (received) => this.receivedTrainingsSubject.next(received),
            error: () => this.receivedTrainingsSubject.next([]),
        });
    }

    updateTrainingGroups(trainingId: string, groupIds: string[]): Observable<Training> {
        return this.http.put<Training>(`${this.apiUrl}/${trainingId}`, { groupIds });
    }

}
