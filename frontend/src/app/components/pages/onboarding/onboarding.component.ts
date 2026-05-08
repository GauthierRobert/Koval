import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthService} from '../../../services/auth.service';

@Component({
    selector: 'app-onboarding',
    standalone: true,
    imports: [CommonModule, FormsModule, TranslateModule],
    templateUrl: './onboarding.component.html',
    styleUrl: './onboarding.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingComponent {
    role: 'ATHLETE' | 'COACH' = 'ATHLETE';
    ftp: number | null = null;
    weightKg: number | null = null;

    cssMinutes: number | null = null;
    cssSeconds: number | null = null;

    thresholdPaceMinutes: number | null = null;
    thresholdPaceSeconds: number | null = null;

    cguAccepted = false;
    saving = false;
    error = '';

    private translate = inject(TranslateService);
    private authService = inject(AuthService);
    private router = inject(Router);

    get cssTotal(): number | undefined {
        if (this.cssMinutes == null && this.cssSeconds == null) return undefined;
        return (this.cssMinutes ?? 0) * 60 + (this.cssSeconds ?? 0);
    }

    get thresholdPaceTotal(): number | undefined {
        if (this.thresholdPaceMinutes == null && this.thresholdPaceSeconds == null) return undefined;
        return (this.thresholdPaceMinutes ?? 0) * 60 + (this.thresholdPaceSeconds ?? 0);
    }

    submit() {
        this.saving = true;
        this.error = '';
        this.authService.completeOnboarding({
            role: this.role,
            ftp: this.ftp ?? undefined,
            weightKg: this.weightKg ?? undefined,
            criticalSwimSpeed: this.cssTotal,
            functionalThresholdPace: this.thresholdPaceTotal,
            cguAccepted: this.cguAccepted,
        }).subscribe({
            next: () => this.router.navigate([this.role === 'COACH' ? '/coach' : '/']),
            error: () => {
                this.saving = false;
                this.error = this.translate.instant('ONBOARDING.ERROR_SAVE_FAILED');
            },
        });
    }
}
