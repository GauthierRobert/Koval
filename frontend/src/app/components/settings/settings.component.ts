import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {AuthService, User} from '../../services/auth.service';

interface PaceField {
    key: string;
    label: string;
    hint: string;
    minutes: number;
    seconds: number;
}

@Component({
    selector: 'app-settings',
    standalone: true,
  imports: [CommonModule, FormsModule],
    templateUrl: './settings.component.html',
    styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {
    private authService = inject(AuthService);

    ftp: number = 250;
    saving = false;
    saved = false;

    runFields: PaceField[] = [
        { key: 'functionalThresholdPace', label: 'Threshold Pace', hint: 'Lactate threshold pace', minutes: 5, seconds: 0 },
        { key: 'pace5k', label: '5K Pace', hint: 'Current 5K race pace', minutes: 4, seconds: 30 },
        { key: 'pace10k', label: '10K Pace', hint: 'Current 10K race pace', minutes: 4, seconds: 45 },
        { key: 'paceHalfMarathon', label: 'Half Marathon Pace', hint: 'Current half marathon race pace', minutes: 5, seconds: 0 },
        { key: 'paceMarathon', label: 'Marathon Pace', hint: 'Current marathon race pace', minutes: 5, seconds: 15 },
    ];

    swimFields: PaceField[] = [
        { key: 'criticalSwimSpeed', label: 'Critical Swim Speed', hint: 'Threshold pace per 100m', minutes: 2, seconds: 0 },
    ];

    ngOnInit() {
        this.authService.user$.subscribe(user => {
            if (user) this.loadFromUser(user);
        });
    }

    private loadFromUser(user: User) {
        this.ftp = user.ftp || 250;

        for (const field of this.runFields) {
            const val = (user as any)[field.key] as number | undefined;
            if (val) {
                field.minutes = Math.floor(val / 60);
                field.seconds = val % 60;
            }
        }

        for (const field of this.swimFields) {
            const val = (user as any)[field.key] as number | undefined;
            if (val) {
                field.minutes = Math.floor(val / 60);
                field.seconds = val % 60;
            }
        }
    }

    private paceToSeconds(field: PaceField): number {
        return (field.minutes || 0) * 60 + (field.seconds || 0);
    }

    formatPace(field: PaceField): string {
        const m = field.minutes || 0;
        const s = field.seconds || 0;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    save() {
        this.saving = true;
        this.saved = false;

        const settings: any = { ftp: this.ftp };
        for (const field of [...this.runFields, ...this.swimFields]) {
            settings[field.key] = this.paceToSeconds(field);
        }

        this.authService.updateSettings(settings).subscribe({
            next: () => {
                this.saving = false;
                this.saved = true;
                setTimeout(() => this.saved = false, 2500);
            },
            error: () => {
                this.saving = false;
            }
        });
    }
}
