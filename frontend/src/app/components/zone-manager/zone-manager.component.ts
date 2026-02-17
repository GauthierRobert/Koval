import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ZoneService} from '../../services/zone.service';
import {AuthService} from '../../services/auth.service';
import {SportType, ZoneReferenceType, ZoneSystem} from '../../services/zone';

@Component({
  selector: 'app-zone-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './zone-manager.component.html',
  styleUrls: ['./zone-manager.component.css']
})
export class ZoneManagerComponent implements OnInit {
  private zoneService = inject(ZoneService);
  private authService = inject(AuthService);

  zoneSystems: ZoneSystem[] = [];
  selectedSystem: ZoneSystem | null = null;
  saving = false;
  saved = false;

  sportTypes: SportType[] = ['CYCLING', 'RUNNING', 'SWIMMING'];
  referenceTypes: ZoneReferenceType[] = [
    'FTP', 'THRESHOLD_PACE', 'CSS',
    'PACE_5K', 'PACE_10K', 'PACE_HALF_MARATHON', 'PACE_MARATHON'
  ];

  ngOnInit() {
    this.loadZoneSystems();
  }

  loadZoneSystems() {
    this.zoneService.getCoachZoneSystems().subscribe({
      next: systems => {
        this.zoneSystems = systems;
      },
      error: err => {
        console.error('Failed to load zone systems', err);
      }
    });
  }

  createNewSystem(sport: SportType) {
    const user = this.authService.currentUser;
    if (!user) return;

    const newSystem: ZoneSystem = {
      name: `New ${sport.charAt(0) + sport.slice(1).toLowerCase()} System`,
      coachId: user.id,
      sportType: sport,
      referenceType: 'FTP',
      zones: [
        {label: 'Z1', low: 0, high: 55, description: 'Recovery'},
        {label: 'Z2', low: 56, high: 75, description: 'Endurance'},
        {label: 'Z3', low: 76, high: 90, description: 'Tempo'},
        {label: 'Z4', low: 91, high: 105, description: 'Threshold'},
        {label: 'Z5', low: 106, high: 120, description: 'VO2Max'},
        {label: 'Z6', low: 120, high: 150, description: 'Long Sprint'},
        {label: 'Z7', low: 150, high: 1000, description: 'Short Sprint'}
      ],
    };

    this.zoneService.createZoneSystem(newSystem).subscribe({
      next: saved => {
        this.zoneSystems.push(saved);
        this.selectSystem(saved);
      },
      error: err => console.error('Failed to create zone system', err)
    });
  }

  selectSystem(system: ZoneSystem) {
    this.selectedSystem = {...system, zones: system.zones.map(z => ({...z}))};
    this.saved = false;
  }

  saveSystem() {
    if (!this.selectedSystem || !this.selectedSystem.id) return;
    this.saving = true;
    this.saved = false;

    this.zoneService.updateZoneSystem(this.selectedSystem.id, this.selectedSystem).subscribe({
      next: updated => {
        const index = this.zoneSystems.findIndex(s => s.id === updated.id);
        if (index !== -1) {
          this.zoneSystems[index] = updated;
        }
        this.selectedSystem = {...updated, zones: updated.zones.map(z => ({...z}))};
        this.saving = false;
        this.saved = true;
        setTimeout(() => this.saved = false, 2500);
      },
      error: err => {
        console.error('Failed to save zone system', err);
        this.saving = false;
      }
    });
  }

  deleteSystem(id: string) {
    if (!confirm('Are you sure you want to delete this zone system?')) return;

    this.zoneService.deleteZoneSystem(id).subscribe({
      next: () => {
        this.zoneSystems = this.zoneSystems.filter(s => s.id !== id);
        if (this.selectedSystem?.id === id) {
          this.selectedSystem = null;
        }
      },
      error: err => console.error('Failed to delete zone system', err)
    });
  }

  addZone() {
    if (!this.selectedSystem) return;
    this.selectedSystem.zones.push({
      label: `Z${this.selectedSystem.zones.length + 1}`,
      low: 0,
      high: 0
    });
  }

  removeZone(index: number) {
    if (!this.selectedSystem) return;
    this.selectedSystem.zones.splice(index, 1);
  }

  moveZone(index: number, direction: number) {
    if (!this.selectedSystem) return;
    const newIndex = index + direction;
    if (newIndex < 0 || newIndex >= this.selectedSystem.zones.length) return;

    const temp = this.selectedSystem.zones[index];
    this.selectedSystem.zones[index] = this.selectedSystem.zones[newIndex];
    this.selectedSystem.zones[newIndex] = temp;
  }
}
