import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, NgZone, OnInit, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService, User} from '../../../../services/auth.service';
import {ZoneService} from '../../../../services/zone.service';
import {ZoneSystem} from '../../../../services/zone';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';

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
  selector: 'app-reference-values-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SportIconComponent, TranslateModule],
  templateUrl: './reference-values-page.component.html',
  styleUrls: ['./reference-values-page.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReferenceValuesPageComponent implements OnInit {
  private authService = inject(AuthService);
  private zoneService = inject(ZoneService);
  private destroyRef = inject(DestroyRef);
  private cdr = inject(ChangeDetectorRef);
  private ngZone = inject(NgZone);

  user$ = this.authService.user$;

  ftp: number | null = null;
  weightKg: number | null = null;
  vo2maxPower: number | null = null;
  power3MinW: number | null = null;
  power12MinW: number | null = null;
  showCpHelp = false;
  saving = false;
  saved = false;

  showSecondaryRunning = false;
  private customZoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  customZoneSystems$ = this.customZoneSystemsSubject.asObservable();
  customRefValues: Record<string, number | null> = {};

  primaryRunFields: PaceField[] = [
    {key: 'functionalThresholdPace', label: 'Threshold Pace', labelKey: 'SETTINGS.FIELD_THRESHOLD_PACE_LABEL', hint: 'Lactate threshold pace', hintKey: 'SETTINGS.FIELD_THRESHOLD_PACE_HINT', minutes: null, seconds: null},
    {key: 'vo2maxPace', label: 'VO2max Pace', labelKey: 'SETTINGS.FIELD_VO2MAX_PACE_LABEL', hint: 'VO2max intensity pace', hintKey: 'SETTINGS.FIELD_VO2MAX_PACE_HINT', minutes: null, seconds: null},
  ];

  secondaryRunFields: PaceField[] = [
    {key: 'pace5k', label: '5K Pace', labelKey: 'SETTINGS.FIELD_5K_PACE_LABEL', hint: 'Current 5K race pace', hintKey: 'SETTINGS.FIELD_5K_PACE_HINT', minutes: null, seconds: null},
    {key: 'pace10k', label: '10K Pace', labelKey: 'SETTINGS.FIELD_10K_PACE_LABEL', hint: 'Current 10K race pace', hintKey: 'SETTINGS.FIELD_10K_PACE_HINT', minutes: null, seconds: null},
    {key: 'paceHalfMarathon', label: 'Half Marathon Pace', labelKey: 'SETTINGS.FIELD_HALF_MARATHON_PACE_LABEL', hint: 'Current half marathon race pace', hintKey: 'SETTINGS.FIELD_HALF_MARATHON_PACE_HINT', minutes: null, seconds: null},
    {key: 'paceMarathon', label: 'Marathon Pace', labelKey: 'SETTINGS.FIELD_MARATHON_PACE_LABEL', hint: 'Current marathon race pace', hintKey: 'SETTINGS.FIELD_MARATHON_PACE_HINT', minutes: null, seconds: null},
  ];

  swimFields: PaceField[] = [
    {key: 'criticalSwimSpeed', label: 'Critical Swim Speed', labelKey: 'SETTINGS.FIELD_CRITICAL_SWIM_SPEED_LABEL', hint: 'Threshold pace per 100m', hintKey: 'SETTINGS.FIELD_CRITICAL_SWIM_SPEED_HINT', minutes: null, seconds: null},
  ];

  get allRunFields(): PaceField[] {
    return [...this.primaryRunFields, ...this.secondaryRunFields];
  }

  /**
   * Two-parameter critical-power model:
   *   P = CP + W'/t  →  CP = (T1·P1 − T2·P2) / (T1 − T2)
   * For T1 = 3 min, T2 = 12 min, this simplifies to (4·P12 − P3) / 3.
   */
  get derivedCp(): number | null {
    const p3 = this.power3MinW;
    const p12 = this.power12MinW;
    if (p3 == null || p12 == null || p3 <= 0 || p12 <= 0 || p3 <= p12) return null;
    return Math.round((4 * p12 - p3) / 3);
  }

  get derivedWPrimeKj(): number | null {
    const cp = this.derivedCp;
    const p3 = this.power3MinW;
    if (cp == null || p3 == null) return null;
    const j = (p3 - cp) * 180;
    return Math.round(j / 100) / 10;
  }

  get cpTestValid(): boolean {
    return this.derivedCp != null;
  }

  get cpTestInvalidOrder(): boolean {
    return this.power3MinW != null
      && this.power12MinW != null
      && this.power3MinW > 0
      && this.power12MinW > 0
      && this.power3MinW <= this.power12MinW;
  }

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
    this.power3MinW = user.power3MinW ?? null;
    this.power12MinW = user.power12MinW ?? null;

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

  toggleCpHelp(): void {
    this.showCpHelp = !this.showCpHelp;
  }

  save() {
    this.saving = true;
    this.saved = false;

    const settings: any = {
      ftp: this.ftp ?? null,
      weightKg: this.weightKg ?? null,
      vo2maxPower: this.vo2maxPower ?? null,
      power3MinW: this.power3MinW ?? null,
      power12MinW: this.power12MinW ?? null,
    };
    for (const field of [...this.allRunFields, ...this.swimFields]) {
      const val = this.paceToSeconds(field);
      settings[field.key] = val ?? null;
    }

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
        this.cdr.markForCheck();
        this.ngZone.runOutsideAngular(() => {
          setTimeout(() => {
            this.ngZone.run(() => {
              this.saved = false;
              this.cdr.markForCheck();
            });
          }, 2000);
        });
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }
}
