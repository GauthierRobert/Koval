import {Injectable} from '@angular/core';
import {Zone, ZoneSystem, SportType} from './zone';
import {User} from './auth.service';

@Injectable({providedIn: 'root'})
export class ZoneClassificationService {

    readonly ZONE_COLORS = ['#b2bec3', '#3498db', '#2ecc71', '#f1c40f', '#e67e22', '#e74c3c', '#c0392b'];

    readonly WALKING_ZONE_INDEX = -1;
    readonly WALKING_COLOR = '#78909c';
    readonly WALKING_LABEL = 'Walk';
    readonly WALKING_DESCRIPTION = 'Walking';

    /** Absolute speed threshold for running — below this = walking (m/s) */
    readonly RUNNING_WALK_THRESHOLD_MS = 5 / 3.6;

    readonly defaultZonesBySport: Record<SportType, Zone[]> = {
        CYCLING: [
            {label: 'Z1', low: 0, high: 55, description: 'Active Recovery'},
            {label: 'Z2', low: 55, high: 75, description: 'Endurance'},
            {label: 'Z3', low: 75, high: 90, description: 'Tempo'},
            {label: 'Z4', low: 90, high: 105, description: 'Threshold'},
            {label: 'Z5', low: 105, high: 120, description: 'VO2max'},
            {label: 'Z6', low: 120, high: 150, description: 'Anaerobic'},
            {label: 'Z7', low: 150, high: 300, description: 'Neuromuscular'},
        ],
        RUNNING: [
            {label: 'Z1', low: 0, high: 75, description: 'Easy'},
            {label: 'Z2', low: 75, high: 85, description: 'Aerobic'},
            {label: 'Z3', low: 85, high: 95, description: 'Tempo'},
            {label: 'Z4', low: 95, high: 105, description: 'Threshold'},
            {label: 'Z5', low: 105, high: 120, description: 'VO2max'},
        ],
        SWIMMING: [
            {label: 'Z1', low: 0, high: 80, description: 'Recovery'},
            {label: 'Z2', low: 80, high: 90, description: 'Endurance'},
            {label: 'Z3', low: 90, high: 100, description: 'Threshold'},
            {label: 'Z4', low: 100, high: 110, description: 'VO2max'},
            {label: 'Z5', low: 110, high: 130, description: 'Sprint'},
        ],
    };

    /** Convert a FitRecord's primary metric to a percentage of the reference value. */
    recordPercent(power: number, speed: number, sport: SportType, referenceValue: number): number {
        if (sport === 'CYCLING') return (power / referenceValue) * 100;
        return (speed / referenceValue) * 100;
    }

    /** Find which zone index a given percentage falls into. */
    classifyZone(percent: number, zones: Zone[]): number {
        for (let i = 0; i < zones.length; i++) {
            if (percent >= zones[i].low && percent <= zones[i].high) return i;
        }
        if (percent > zones[zones.length - 1].high) return zones.length - 1;
        // Value in a gap — nearest boundary
        let bestIdx = 0;
        let bestDist = Infinity;
        for (let i = 0; i < zones.length; i++) {
            const dist = Math.min(Math.abs(percent - zones[i].low), Math.abs(percent - zones[i].high));
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Classify a single record, with sport-specific overrides.
     * Running: speed below 5 km/h → walking zone.
     */
    classifyRecord(power: number, speed: number, sport: SportType, referenceValue: number, zones: Zone[]): number {
        if (sport === 'RUNNING' && speed < this.RUNNING_WALK_THRESHOLD_MS) {
            return this.WALKING_ZONE_INDEX;
        }
        return this.classifyZone(this.recordPercent(power, speed, sport, referenceValue), zones);
    }

    getZoneColor(zoneIndex: number): string {
        if (zoneIndex === this.WALKING_ZONE_INDEX) return this.WALKING_COLOR;
        return this.ZONE_COLORS[zoneIndex] ?? this.ZONE_COLORS[this.ZONE_COLORS.length - 1];
    }

    getZoneLabel(zoneIndex: number, zones: Zone[]): string {
        if (zoneIndex === this.WALKING_ZONE_INDEX) return this.WALKING_LABEL;
        return zones[zoneIndex]?.label ?? `Z${zoneIndex + 1}`;
    }

    getZoneDescription(zoneIndex: number, zones: Zone[]): string {
        if (zoneIndex === this.WALKING_ZONE_INDEX) return this.WALKING_DESCRIPTION;
        return zones[zoneIndex]?.description ?? '';
    }

    /**
     * Distance between two zone indices for merge eligibility.
     * Walking zones never merge with regular zones.
     */
    zoneDistance(a: number, b: number): number {
        if ((a === this.WALKING_ZONE_INDEX) !== (b === this.WALKING_ZONE_INDEX)) return Infinity;
        return Math.abs(a - b);
    }

    /** Resolve the effective zones and reference value for a sport + user + optional custom zone system. */
    resolveZonesAndReference(
        sport: SportType, user: User | null, selectedId: string | null, userSystems: ZoneSystem[],
    ): {zones: Zone[]; referenceValue: number} | null {
        let zones: Zone[];
        if (selectedId) {
            const custom = userSystems.find(s => s.id === selectedId);
            zones = custom ? custom.zones : this.defaultZonesBySport[sport];
        } else {
            zones = this.defaultZonesBySport[sport];
        }

        let referenceValue: number | null = null;
        if (sport === 'CYCLING') {
            referenceValue = user?.ftp ?? null;
        } else if (sport === 'RUNNING') {
            const ftpPace = user?.functionalThresholdPace;
            referenceValue = ftpPace ? 1000 / ftpPace : null;
        } else if (sport === 'SWIMMING') {
            const css = user?.criticalSwimSpeed;
            referenceValue = css ? 100 / css : null;
        }

        if (!referenceValue || referenceValue <= 0) return null;
        return {zones, referenceValue};
    }
}
