import {Injectable} from '@angular/core';
import {Zone, ZoneSystem, SportType} from './zone';
import {User} from './auth.service';
import {WorkoutBlock} from '../models/training.model';

/**
 * Color stops along the intensity-percent axis, per sport. Stops are positioned so that each sport's
 * default zone means land on a distinct palette color: Z2→blue, Z3→green, Z4→yellow, Z5→orange, and
 * for cycling Z6→red, Z7→dark red. Run/swim (5 zones) reserve red / dark red for efforts above Z5.
 */
export const INTENSITY_COLOR_STOPS_BY_SPORT: Record<SportType, ReadonlyArray<{percent: number; hex: string}>> = {
    CYCLING: [
        {percent: 0,   hex: '#b2bec3'}, // gray      — recovery
        {percent: 67,  hex: '#3498db'}, // blue      — endurance (Z2 mean ~65)
        {percent: 80,  hex: '#2ecc71'}, // green     — tempo     (Z3 mean ~82)
        {percent: 90,  hex: '#f1c40f'}, // yellow    — threshold (Z4 mean ~97)
        {percent: 102, hex: '#e67e22'}, // orange    — VO2max    (Z5 mean ~112)
        {percent: 120, hex: '#e74c3c'}, // red       — anaerobic (Z6 mean ~135)
        {percent: 150, hex: '#c0392b'}, // dark red  — neuromuscular (Z7)
    ],
    RUNNING: [
        {percent: 0,   hex: '#b2bec3'}, // gray      — Z1 easy    (mean ~37)
        {percent: 78,  hex: '#3498db'}, // blue      — Z2 aerobic (mean 80)
        {percent: 86,  hex: '#2ecc71'}, // green     — Z3 tempo   (mean 90)
        {percent: 96, hex: '#f1c40f'}, // yellow    — Z4 threshold (mean 100)
        {percent: 105, hex: '#e67e22'}, // orange    — Z5 VO2max  (mean ~112)
        {percent: 120, hex: '#e74c3c'}, // red       — above Z5
        {percent: 150, hex: '#c0392b'}, // dark red  — extreme
    ],
    SWIMMING: [
        {percent: 0,   hex: '#b2bec3'}, // gray      — Z1 recovery  (mean ~40)
        {percent: 85,  hex: '#3498db'}, // blue      — Z2 endurance (mean 85)
        {percent: 95,  hex: '#2ecc71'}, // green     — Z3 threshold (mean 95)
        {percent: 105, hex: '#f1c40f'}, // yellow    — Z4 VO2max    (mean 105)
        {percent: 120, hex: '#e67e22'}, // orange    — Z5 sprint    (mean 120)
        {percent: 135, hex: '#e74c3c'}, // red       — above Z5
        {percent: 160, hex: '#c0392b'}, // dark red  — extreme
    ],
};

const NEUTRAL_COLOR = '#636e72';
const TRANSITION_COLOR = '#fd79a8';

function hexToRgb(hex: string): [number, number, number] {
    const h = hex.replace('#', '');
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

function rgbToHex(r: number, g: number, b: number): string {
    const c = (n: number) => Math.round(Math.max(0, Math.min(255, n))).toString(16).padStart(2, '0');
    return `#${c(r)}${c(g)}${c(b)}`;
}

/**
 * Percent → color along the zone palette (gray → blue → green → yellow → orange → red → dark red).
 * Stops are tuned per sport so zones land on the expected palette color.
 */
export function intensityToColor(percent: number, sport: SportType = 'CYCLING'): string {
    const stops = INTENSITY_COLOR_STOPS_BY_SPORT[sport];
    if (percent <= stops[0].percent) return stops[0].hex;
    if (percent >= stops[stops.length - 1].percent) return stops[stops.length - 1].hex;
    for (let i = 1; i < stops.length; i++) {
        const hi = stops[i];
        if (percent <= hi.percent) {
            const lo = stops[i - 1];
            const t = (percent - lo.percent) / (hi.percent - lo.percent);
            const [lr, lg, lb] = hexToRgb(lo.hex);
            const [hr, hg, hb] = hexToRgb(hi.hex);
            return rgbToHex(lr + (hr - lr) * t, lg + (hg - lg) * t, lb + (hb - lb) * t);
        }
    }
    return stops[stops.length - 1].hex;
}

/** The percent used for coloring a block. RAMP uses mean(start,end). Null when the block has no intensity. */
export function blockColorIntensity(block: WorkoutBlock): number | null {
    if (block.type === 'PAUSE' || block.type === 'FREE' || block.type === 'TRANSITION') return null;
    if (block.type === 'RAMP') {
        const s = block.intensityStart;
        const e = block.intensityEnd;
        if (s == null && e == null) return null;
        return ((s ?? 0) + (e ?? 0)) / 2;
    }
    return block.intensityTarget ?? null;
}

/** Color for a WorkoutBlock — intensity spectrum when available, neutral otherwise. Pure. */
export function blockColor(block: WorkoutBlock, sport: SportType = 'CYCLING'): string {
    if (block.type === 'TRANSITION') return TRANSITION_COLOR;
    const intensity = blockColorIntensity(block);
    if (intensity == null) return NEUTRAL_COLOR;
    return intensityToColor(intensity, sport);
}

@Injectable({providedIn: 'root'})
export class ZoneClassificationService {

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

    /**
     * Find which zone index a given percentage falls into.
     *
     * Boundaries are treated as [low, high): a value equal to the boundary belongs to the
     * upper zone, so 90% on CYCLING defaults lands in Z4 (Threshold) not Z3 (Tempo).
     * The top zone is inclusive on both ends so its ceiling is reachable.
     */
    classifyZone(percent: number, zones: Zone[]): number {
        const last = zones.length - 1;
        for (let i = 0; i < zones.length; i++) {
            const inUpper = i === last ? percent <= zones[i].high : percent < zones[i].high;
            if (percent >= zones[i].low && inUpper) return i;
        }
        if (percent > zones[last].high) return last;
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

    /** Spectrum color from a raw intensity percent. */
    intensityToColor(percent: number, sport: SportType = 'CYCLING'): string {
        return intensityToColor(percent, sport);
    }

    /** Spectrum color for a WorkoutBlock. Uses intensityTarget (mean of start/end for RAMP). */
    blockColor(block: WorkoutBlock, sport: SportType = 'CYCLING'): string {
        return blockColor(block, sport);
    }

    /** Color for a zone — derived from the zone's mean intensity. */
    getZoneColor(zoneIndex: number, zones?: Zone[], sport: SportType = 'CYCLING'): string {
        if (zoneIndex === this.WALKING_ZONE_INDEX) return this.WALKING_COLOR;
        const zs = zones ?? this.defaultZonesBySport[sport];
        const z = zs[zoneIndex];
        if (!z) return intensityToColor(999, sport);
        return intensityToColor((z.low + z.high) / 2, sport);
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
