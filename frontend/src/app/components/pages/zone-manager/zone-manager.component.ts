import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {map} from 'rxjs/operators';
import {Router} from '@angular/router';
import {ZoneService} from '../../../services/zone.service';
import {ZoneClassificationService} from '../../../services/zone-classification.service';
import {AuthService} from '../../../services/auth.service';
import {SportType, Zone, ZoneReferenceType, ZoneSystem} from '../../../services/zone';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {CreateWithAiModalComponent} from '../../shared/create-with-ai-modal/create-with-ai-modal.component';
import {ActionResult} from '../../../services/ai-action.service';

@Component({
  selector: 'app-zone-manager',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent, CreateWithAiModalComponent],
  templateUrl: './zone-manager.component.html',
  styleUrls: ['./zone-manager.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoneManagerComponent implements OnInit {
  @Input() embedded = false;
  @Output() closed = new EventEmitter<void>();

  private zoneService = inject(ZoneService);
  private zoneCls = inject(ZoneClassificationService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private translate = inject(TranslateService);

  private zoneSystemsSubject = new BehaviorSubject<ZoneSystem[]>([]);
  private sportFilterSubject = new BehaviorSubject<SportType | null>(null);

  zoneSystems$ = this.zoneSystemsSubject.asObservable();
  filteredSystems$ = combineLatest([this.zoneSystemsSubject, this.sportFilterSubject]).pipe(
    map(([systems, filter]) => filter ? systems.filter(s => s.sportType === filter) : systems)
  );

  get activeSportFilter(): SportType | null { return this.sportFilterSubject.value; }

  selectedSystem: ZoneSystem | null = null;
  editingSystem: ZoneSystem | null = null;
  saving = false;
  saved = false;
  deleting = false;
  showCreateMenu = false;
  showAiModal = false;

  sportTypes: SportType[] = ['CYCLING', 'RUNNING', 'SWIMMING'];

  get referenceTypesBySport(): Record<SportType, { value: ZoneReferenceType; label: string }[]> {
    return {
      CYCLING: [
        { value: 'FTP', label: this.translate.instant('ZONE_MANAGER.REF_FTP_LABEL') },
        { value: 'VO2MAX_POWER', label: this.translate.instant('ZONE_MANAGER.REF_VO2MAX_POWER_LABEL') },
        { value: 'CUSTOM', label: this.translate.instant('ZONE_MANAGER.REF_CUSTOM_LABEL') },
      ],
      RUNNING: [
        { value: 'THRESHOLD_PACE', label: this.translate.instant('ZONE_MANAGER.REF_THRESHOLD_PACE_LABEL') },
        { value: 'VO2MAX_PACE', label: this.translate.instant('ZONE_MANAGER.REF_VO2MAX_PACE_LABEL') },
        { value: 'PACE_5K', label: this.translate.instant('ZONE_MANAGER.REF_PACE_5K_LABEL') },
        { value: 'PACE_10K', label: this.translate.instant('ZONE_MANAGER.REF_PACE_10K_LABEL') },
        { value: 'PACE_HALF_MARATHON', label: this.translate.instant('ZONE_MANAGER.REF_PACE_HALF_MARATHON_LABEL') },
        { value: 'PACE_MARATHON', label: this.translate.instant('ZONE_MANAGER.REF_PACE_MARATHON_LABEL') },
        { value: 'CUSTOM', label: this.translate.instant('ZONE_MANAGER.REF_CUSTOM_LABEL') },
      ],
      SWIMMING: [
        { value: 'CSS', label: this.translate.instant('ZONE_MANAGER.REF_CSS_LABEL') },
        { value: 'CUSTOM', label: this.translate.instant('ZONE_MANAGER.REF_CUSTOM_LABEL') },
      ],
    };
  }

  readonly defaultZonesBySport: Record<SportType, Zone[]> = {
    CYCLING: [
      { label: 'Z1', low: 0, high: 55, description: 'Active Recovery' },
      { label: 'Z2', low: 56, high: 75, description: 'Endurance' },
      { label: 'Z3', low: 76, high: 90, description: 'Tempo' },
      { label: 'Z4', low: 91, high: 105, description: 'Threshold' },
      { label: 'Z5', low: 106, high: 120, description: 'VO2max' },
      { label: 'Z6', low: 121, high: 150, description: 'Anaerobic' },
      { label: 'Z7', low: 151, high: 300, description: 'Neuromuscular' },
    ],
    RUNNING: [
      { label: 'Z1', low: 0, high: 75, description: 'Easy' },
      { label: 'Z2', low: 76, high: 85, description: 'Aerobic' },
      { label: 'Z3', low: 86, high: 95, description: 'Tempo' },
      { label: 'Z4', low: 96, high: 105, description: 'Threshold' },
      { label: 'Z5', low: 106, high: 120, description: 'VO2max' },
    ],
    SWIMMING: [
      { label: 'Z1', low: 0, high: 80, description: 'Recovery' },
      { label: 'Z2', low: 81, high: 90, description: 'Endurance' },
      { label: 'Z3', low: 91, high: 100, description: 'Threshold' },
      { label: 'Z4', low: 101, high: 110, description: 'VO2max' },
      { label: 'Z5', low: 111, high: 130, description: 'Sprint' },
    ],
  };

  ngOnInit() {
    this.loadZoneSystems();
  }

  loadZoneSystems() {
    this.zoneService.getCoachZoneSystems().subscribe({
      next: (systems) => this.zoneSystemsSubject.next(systems),
      error: (err) => console.error('Failed to load zone systems', err),
    });
  }

  get availableReferenceTypes(): { value: ZoneReferenceType; label: string }[] {
    if (!this.editingSystem) return [];
    return this.referenceTypesBySport[this.editingSystem.sportType] || [];
  }

  setSportFilter(sport: SportType | null) {
    this.sportFilterSubject.next(this.sportFilterSubject.value === sport ? null : sport);
  }

  createNewSystem(sport: SportType) {
    const user = this.authService.currentUser;
    if (!user) return;

    const defaultRef: Record<SportType, ZoneReferenceType> = {
      CYCLING: 'FTP',
      RUNNING: 'THRESHOLD_PACE',
      SWIMMING: 'CSS',
    };

    const newSystem: ZoneSystem = {
      name: `${sport.charAt(0) + sport.slice(1).toLowerCase()} Zones`,
      coachId: user.id,
      sportType: sport,
      referenceType: defaultRef[sport],
      zones: this.defaultZonesBySport[sport].map((z) => ({ ...z })),
    };

    this.zoneService.createZoneSystem(newSystem).subscribe({
      next: (saved) => {
        this.zoneSystemsSubject.next([...this.zoneSystemsSubject.value, saved]);
        this.selectSystem(saved);
        this.showCreateMenu = false;
      },
      error: (err) => console.error('Failed to create zone system', err),
    });
  }

  selectSystem(system: ZoneSystem) {
    this.selectedSystem = system;
    this.editingSystem = { ...system, zones: system.zones.map((z) => ({ ...z })) };
    this.saved = false;
  }

  deselectSystem() {
    this.selectedSystem = null;
    this.editingSystem = null;
  }

  saveSystem() {
    if (!this.editingSystem?.id) return;
    this.saving = true;
    this.saved = false;

    const defaultChanged = this.editingSystem.defaultForSport !== this.selectedSystem?.defaultForSport;

    this.zoneService.updateZoneSystem(this.editingSystem.id, this.editingSystem).subscribe({
      next: (updated) => {
        const finalize = (final: ZoneSystem) => {
          const systems = this.zoneSystemsSubject.value;
          const index = systems.findIndex((s) => s.id === final.id);
          if (index !== -1) {
            const next = [...systems];
            next[index] = final;
            this.zoneSystemsSubject.next(next);
          }
          this.selectedSystem = final;
          this.editingSystem = { ...final, zones: final.zones.map((z) => ({ ...z })) };
          this.saving = false;
          this.saved = true;
          this.loadZoneSystems();
          setTimeout(() => (this.saved = false), 2500);
        };

        if (defaultChanged && updated.id) {
          this.zoneService.setDefaultForSport(updated.id, !!updated.defaultForSport).subscribe({
            next: (defaultUpdated) => finalize(defaultUpdated),
            error: () => finalize(updated),
          });
        } else {
          finalize(updated);
        }
      },
      error: (err) => {
        console.error('Failed to save zone system', err);
        this.saving = false;
      },
    });
  }

  deleteSystem() {
    if (!this.selectedSystem?.id) return;
    this.deleting = true;

    this.zoneService.deleteZoneSystem(this.selectedSystem.id).subscribe({
      next: () => {
        this.zoneSystemsSubject.next(this.zoneSystemsSubject.value.filter((s) => s.id !== this.selectedSystem!.id));
        this.selectedSystem = null;
        this.editingSystem = null;
        this.deleting = false;
      },
      error: (err) => {
        console.error('Failed to delete zone system', err);
        this.deleting = false;
      },
    });
  }

  addZone() {
    if (!this.editingSystem) return;
    const lastZone = this.editingSystem.zones[this.editingSystem.zones.length - 1];
    this.editingSystem.zones.push({
      label: `Z${this.editingSystem.zones.length + 1}`,
      low: lastZone ? lastZone.high + 1 : 0,
      high: lastZone ? lastZone.high + 20 : 50,
      description: '',
    });
  }

  removeZone(index: number) {
    if (!this.editingSystem) return;
    this.editingSystem.zones.splice(index, 1);
  }

  getZoneColor(index: number): string {
    return this.zoneCls.getZoneColor(
      index,
      this.editingSystem?.zones,
      this.editingSystem?.sportType ?? 'CYCLING',
    );
  }

  /** Color for a zone inside a system card in the list (not the currently-edited one). */
  getZoneColorForSystem(index: number, sys: ZoneSystem): string {
    return this.zoneCls.getZoneColor(index, sys.zones, sys.sportType);
  }

  getZoneWidth(zone: Zone): number {
    const maxHigh = this.editingSystem
      ? Math.max(...this.editingSystem.zones.map((z) => z.high), 100)
      : 100;
    return Math.max(((zone.high - zone.low) / maxHigh) * 100, 8);
  }

  getSportLabel(sport: SportType): string {
    return sport.charAt(0) + sport.slice(1).toLowerCase();
  }

  getDefaultUnit(referenceType: ZoneReferenceType): string {
    switch (referenceType) {
      case 'FTP':
      case 'VO2MAX_POWER':
        return this.translate.instant('ZONE_MANAGER.UNIT_WATTS');
      case 'THRESHOLD_PACE':
      case 'VO2MAX_PACE':
      case 'PACE_5K':
      case 'PACE_10K':
      case 'PACE_HALF_MARATHON':
      case 'PACE_MARATHON':
        return this.translate.instant('ZONE_MANAGER.UNIT_MIN_PER_KM');
      case 'CSS':
        return this.translate.instant('ZONE_MANAGER.UNIT_MIN_PER_100M');
      default:
        return '';
    }
  }

  onAiZoneCreated(_result: ActionResult): void {
    this.showAiModal = false;
    this.loadZoneSystems();
  }

  goToAthletes() {
    this.router.navigate(['/coach']);
  }

  trackByIndex(index: number): number {
    return index;
  }
}
