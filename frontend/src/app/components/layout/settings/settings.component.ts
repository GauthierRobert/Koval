import {ChangeDetectionStrategy, Component, DestroyRef, HostListener, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {AuthService, User} from '../../../services/auth.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {TranslateModule} from '@ngx-translate/core';
import {A11yModule} from '@angular/cdk/a11y';

interface PaceField {
    key: string;
    label: string;
    labelKey: string;
    hint: string;
    hintKey: string;
    minutes: number | null;
    seconds: number | null;
}

@Component({
    selector: 'app-settings',
    standalone: true,
    imports: [CommonModule, FormsModule, SportIconComponent, TranslateModule, A11yModule],
    templateUrl: './settings.component.html',
    styleUrls: ['./settings.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent implements OnInit {
    private authService = inject(AuthService);
    private zoneService = inject(ZoneService);
    private destroyRef = inject(DestroyRef);

    user$ = this.authService.user$;

    ftp: number | null = null;
    weightKg: number | null = null;
    vo2maxPower: number | null = null;
    aiPrePrompt = '';
    aiPrePromptEnabled = false;
    isCoach = false;
    saving = false;
    saved = false;

    showSecondaryRunning = false;
    private customZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
    customZoneSystems$ = this.customZoneSystemsSubject.asObservable();
    customRefValues: Record<string, number | null> = {};

    primaryRunFields: PaceField[] = [
        { key: 'functionalThresholdPace', label: 'Threshold Pace', labelKey: 'SETTINGS.FIELD_THRESHOLD_PACE_LABEL', hint: 'Lactate threshold pace', hintKey: 'SETTINGS.FIELD_THRESHOLD_PACE_HINT', minutes: null, seconds: null },
        { key: 'vo2maxPace', label: 'VO2max Pace', labelKey: 'SETTINGS.FIELD_VO2MAX_PACE_LABEL', hint: 'VO2max intensity pace', hintKey: 'SETTINGS.FIELD_VO2MAX_PACE_HINT', minutes: null, seconds: null },
    ];

    secondaryRunFields: PaceField[] = [
        { key: 'pace5k', label: '5K Pace', labelKey: 'SETTINGS.FIELD_5K_PACE_LABEL', hint: 'Current 5K race pace', hintKey: 'SETTINGS.FIELD_5K_PACE_HINT', minutes: null, seconds: null },
        { key: 'pace10k', label: '10K Pace', labelKey: 'SETTINGS.FIELD_10K_PACE_LABEL', hint: 'Current 10K race pace', hintKey: 'SETTINGS.FIELD_10K_PACE_HINT', minutes: null, seconds: null },
        { key: 'paceHalfMarathon', label: 'Half Marathon Pace', labelKey: 'SETTINGS.FIELD_HALF_MARATHON_PACE_LABEL', hint: 'Current half marathon race pace', hintKey: 'SETTINGS.FIELD_HALF_MARATHON_PACE_HINT', minutes: null, seconds: null },
        { key: 'paceMarathon', label: 'Marathon Pace', labelKey: 'SETTINGS.FIELD_MARATHON_PACE_LABEL', hint: 'Current marathon race pace', hintKey: 'SETTINGS.FIELD_MARATHON_PACE_HINT', minutes: null, seconds: null },
    ];

    get allRunFields(): PaceField[] {
        return [...this.primaryRunFields, ...this.secondaryRunFields];
    }

    swimFields: PaceField[] = [
        { key: 'criticalSwimSpeed', label: 'Critical Swim Speed', labelKey: 'SETTINGS.FIELD_CRITICAL_SWIM_SPEED_LABEL', hint: 'Threshold pace per 100m', hintKey: 'SETTINGS.FIELD_CRITICAL_SWIM_SPEED_HINT', minutes: null, seconds: null },
    ];

    ngOnInit() {
        this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
            if (user) this.loadFromUser(user);
        });

        this.zoneService.getMyZoneSystems().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: (systems) => {
                this.customZoneSystemsSubject.next(systems.filter(s => s.referenceType === 'CUSTOM'));
            },
            error: () => this.customZoneSystemsSubject.next([]),
        });
    }

    private loadFromUser(user: User) {
        this.ftp = user.ftp ?? null;
        this.weightKg = user.weightKg ?? null;
        this.vo2maxPower = user.vo2maxPower ?? null;
        this.isCoach = user.role === 'COACH';
        this.aiPrePrompt = user.aiPrePrompt ?? '';
        this.aiPrePromptEnabled = user.aiPrePromptEnabled ?? false;

        for (const field of [...this.allRunFields, ...this.swimFields]) {
            const val = (user as any)[field.key] as number | undefined;
            if (val) {
                field.minutes = Math.floor(val / 60);
                field.seconds = val % 60;
            } else {
                field.minutes = null;
                field.seconds = null;
            }
        }

        if (user.customZoneReferenceValues) {
            for (const [key, value] of Object.entries(user.customZoneReferenceValues)) {
                this.customRefValues[key] = value ?? null;
            }
        }
    }

    /** Returns total seconds if the field has any value entered, otherwise undefined (not set) */
    private paceToSeconds(field: PaceField): number | undefined {
        if (field.minutes == null && field.seconds == null) return undefined;
        return (field.minutes ?? 0) * 60 + (field.seconds ?? 0);
    }

    isFieldSet(field: PaceField): boolean {
        return field.minutes != null || field.seconds != null;
    }

    formatPace(field: PaceField): string {
        const m = field.minutes ?? 0;
        const s = field.seconds ?? 0;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    close() {
        this.authService.toggleSettings(false);
    }

    @HostListener('document:keydown.escape')
    onEscapeKey(): void {
        this.close();
    }

    save() {
        this.saving = true;
        this.saved = false;

        const settings: any = {
            ftp: this.ftp ?? null,
            weightKg: this.weightKg ?? null,
            vo2maxPower: this.vo2maxPower ?? null,
            ...(this.isCoach ? { aiPrePrompt: this.aiPrePrompt.trim() || null, aiPrePromptEnabled: this.aiPrePromptEnabled } : {}),
        };
        for (const field of [...this.allRunFields, ...this.swimFields]) {
            const val = this.paceToSeconds(field);
            settings[field.key] = val ?? null;
        }

        // Build custom zone reference values map (only non-null entries)
        const customZoneReferenceValues: Record<string, number> = {};
        for (const [key, value] of Object.entries(this.customRefValues)) {
            if (value != null) {
                customZoneReferenceValues[key] = value;
            }
        }
        if (Object.keys(customZoneReferenceValues).length > 0) {
            settings.customZoneReferenceValues = customZoneReferenceValues;
        }

        this.authService.updateSettings(settings).subscribe({
            next: () => {
                this.saving = false;
                this.saved = true;
                this.close();
            },
            error: () => {
                this.saving = false;
            }
        });
    }
}
