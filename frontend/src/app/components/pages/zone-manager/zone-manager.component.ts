import { ChangeDetectionStrategy, Component, inject, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';
import { ZoneService } from '../../../services/zone.service';
import { AuthService } from '../../../services/auth.service';
import { SportType, ZoneReferenceType, ZoneSystem, Zone } from '../../../services/zone';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import { CreateWithAiModalComponent } from '../../shared/create-with-ai-modal/create-with-ai-modal.component';
import { ActionResult } from '../../../services/ai-action.service';

@Component({
  selector: 'app-zone-manager',
  standalone: true,
  imports: [CommonModule, FormsModule, SportIconComponent, CreateWithAiModalComponent],
  templateUrl: './zone-manager.component.html',
  styleUrls: ['./zone-manager.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoneManagerComponent implements OnInit {
  @Input() embedded = false;
  @Output() closed = new EventEmitter<void>();

  private zoneService = inject(ZoneService);
  private authService = inject(AuthService);
  private router = inject(Router);

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

  referenceTypesBySport: Record<SportType, { value: ZoneReferenceType; label: string }[]> = {
    CYCLING: [
      { value: 'FTP', label: 'FTP (% of Threshold Power)' },
      { value: 'VO2MAX_POWER', label: 'VO2max Power' },
      { value: 'CUSTOM', label: 'Custom' },
    ],
    RUNNING: [
      { value: 'THRESHOLD_PACE', label: 'Threshold Pace' },
      { value: 'VO2MAX_PACE', label: 'VO2max Pace' },
      { value: 'PACE_5K', label: '5K Pace' },
      { value: 'PACE_10K', label: '10K Pace' },
      { value: 'PACE_HALF_MARATHON', label: 'Half Marathon Pace' },
      { value: 'PACE_MARATHON', label: 'Marathon Pace' },
      { value: 'CUSTOM', label: 'Custom' },
    ],
    SWIMMING: [
      { value: 'CSS', label: 'CSS (Critical Swim Speed)' },
      { value: 'CUSTOM', label: 'Custom' },
    ],
  };

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

  readonly zoneColors = [
    '#6366f1', '#3b82f6', '#22c55e', '#eab308', '#f97316', '#ef4444', '#dc2626',
  ];

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
    return this.zoneColors[index % this.zoneColors.length];
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
