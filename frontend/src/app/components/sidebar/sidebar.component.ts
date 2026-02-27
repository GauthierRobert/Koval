import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {BluetoothService} from '../../services/bluetooth.service';
import {AuthService} from '../../services/auth.service';
import {CoachService} from '../../services/coach.service';
import {HistoryService} from '../../services/history.service';
import {TrainingService} from '../../services/training.service';
import {TrainingHistoryComponent} from '../training-history/training-history.component';
import {WorkoutHistoryComponent} from '../workout-history/workout-history.component';

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [CommonModule, FormsModule, TrainingHistoryComponent, WorkoutHistoryComponent],
    templateUrl: './sidebar.component.html',
    styleUrl: './sidebar.component.css',
})
export class SidebarComponent {
    private bluetoothService = inject(BluetoothService);
    private authService = inject(AuthService);
    private coachService = inject(CoachService);

    viewMode: 'plans' | 'history' = 'plans';
    user$ = this.authService.user$;
    inviteCode = '';
    joinError = '';
    private joiningSubject = new BehaviorSubject<boolean>(false);
    joining$ = this.joiningSubject.asObservable();

    private historyService = inject(HistoryService);
    private trainingService = inject(TrainingService);

    switchView(mode: 'plans' | 'history'): void {
        this.viewMode = mode;
        if (mode === 'history') {
            // Clear training selection; history panel will show selected session
            this.trainingService.selectTraining(null);
        } else {
            // Clear session selection; plans panel will show selected training
            this.historyService.selectSession(null);
        }
    }

    toggleSimulation(event: any) {
        this.bluetoothService.toggleSimulation(event.target.checked);
    }

    getInitials(name: string): string {
        return name?.split(' ').map((n) => n[0]).join('').substring(0, 2).toUpperCase() || '?';
    }

    logout(): void {
        this.authService.logout();
    }

    redeemCode(): void {
        if (!this.inviteCode.trim()) return;
        this.joiningSubject.next(true);
        this.joinError = '';
        this.coachService.redeemInviteCode(this.inviteCode.trim()).subscribe({
            next: () => {
                this.joiningSubject.next(false);
                this.inviteCode = '';
                this.authService.refreshUser();
            },
            error: () => {
                this.joiningSubject.next(false);
                this.joinError = 'Invalid or expired code';
            },
        });
    }
}
