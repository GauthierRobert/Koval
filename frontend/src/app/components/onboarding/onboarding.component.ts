import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-onboarding',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './onboarding.component.html',
    styleUrl: './onboarding.component.css',
})
export class OnboardingComponent {
    role: 'ATHLETE' | 'COACH' = 'ATHLETE';
    ftp: number | null = null;

    cssMinutes = 2;
    cssSeconds = 0;

    thresholdPaceMinutes = 5;
    thresholdPaceSeconds = 0;

    saving = false;
    error = '';

    constructor(private authService: AuthService, private router: Router) {}

    get cssSeconds_total(): number {
        return this.cssMinutes * 60 + this.cssSeconds;
    }

    get thresholdPaceSeconds_total(): number {
        return this.thresholdPaceMinutes * 60 + this.thresholdPaceSeconds;
    }

    submit() {
        this.saving = true;
        this.error = '';
        this.authService.completeOnboarding({
            role: this.role,
            ftp: this.ftp || undefined,
            criticalSwimSpeed: this.cssSeconds_total || undefined,
            functionalThresholdPace: this.thresholdPaceSeconds_total || undefined,
        }).subscribe({
            next: () => this.router.navigate([this.role === 'COACH' ? '/coach' : '/']),
            error: () => {
                this.saving = false;
                this.error = 'Failed to save. Please try again.';
            },
        });
    }
}
