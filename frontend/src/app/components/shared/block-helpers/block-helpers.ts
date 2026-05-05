import {flattenElements, Training, WorkoutBlock} from '../../../models/training.model';
import {blockColor as sharedBlockColor} from '../../../services/zone-classification.service';
import {SportType} from '../../../services/zone';

/**
 * Pure utility functions for computing workout block visual properties.
 * These functions have no service dependencies and can be reused across components.
 */

export function getMaxIntensity(training: Training): number {
  if (!training.blocks) return 150;
  const flat = flattenElements(training.blocks);
  const intensities = flat.flatMap((b) => [
    b.intensityTarget || 0,
    b.intensityStart || 0,
    b.intensityEnd || 0,
  ]);
  const maxBlockIntensity = intensities.length > 0 ? Math.max(...intensities) : 0;
  return Math.max(150, maxBlockIntensity + 20);
}

export function getBlockHeight(block: WorkoutBlock, maxI: number): number {
  if (block.type === 'PAUSE') return 100;
  if (block.type === 'FREE') return (65 / maxI) * 100;
  if (block.type === 'TRANSITION') return (30 / maxI) * 100;
  const intensity =
    block.type === 'RAMP'
      ? Math.max(block.intensityStart || 0, block.intensityEnd || 0)
      : block.intensityTarget || 0;
  return (intensity / maxI) * 100;
}

export function getBlockClipPath(block: WorkoutBlock, maxI: number): string {
  if (block.type !== 'RAMP') return 'none';
  const startH = ((block.intensityStart || 0) / maxI) * 100;
  const endH = ((block.intensityEnd || 0) / maxI) * 100;
  const currentH = Math.max(startH, endH);
  const startRel = 100 - (startH / currentH) * 100;
  const endRel = 100 - (endH / currentH) * 100;
  return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
}

/** Normalize Training.sportType (which may be 'BRICK') to a spectrum SportType. */
export function normalizeSport(sport: string | null | undefined): SportType {
  if (sport === 'RUNNING' || sport === 'SWIMMING') return sport;
  return 'CYCLING';
}

export function getBlockColor(block: WorkoutBlock, sport: string | null | undefined = 'CYCLING'): string {
  return sharedBlockColor(block, normalizeSport(sport));
}

/** Representative intensity % per block type, used for type selector chips / swatches. */
const BLOCK_TYPE_REPRESENTATIVE_INTENSITY: Record<string, number | null> = {
  WARMUP: 55,
  STEADY: 75,
  INTERVAL: 110,
  RAMP: 85,
  COOLDOWN: 40,
  FREE: null,
  PAUSE: null,
  TRANSITION: null,
};

/**
 * Color for a block TYPE alone (e.g., chips in the type selector where no intensity is known).
 * Maps each type to a representative position on the spectrum so the chips still follow the
 * same purple → red gradient used everywhere else.
 */
export function getBlockTypeColor(type: string, sport: string | null | undefined = 'CYCLING'): string {
  return sharedBlockColor(
    {type: type as WorkoutBlock['type'], label: '', intensityTarget: BLOCK_TYPE_REPRESENTATIVE_INTENSITY[type] ?? undefined},
    normalizeSport(sport),
  );
}

export function getDisplayIntensity(block: WorkoutBlock): string {
  if (block.type === 'PAUSE') return 'PAUSE';
  if (block.type === 'FREE') return 'FREE';
  if (block.type === 'TRANSITION') return block.transitionType ?? 'T';
  if (block.type === 'RAMP') return `${block.intensityStart}%-${block.intensityEnd}%`;
  return `${block.intensityTarget}%`;
}

/**
 * Format the "size" of a block: duration (e.g. "5min", "30s") if set, otherwise
 * fall back to distance (e.g. "100m", "1.5km"). Returns "0s" only when the
 * block has neither duration nor distance.
 *
 * Time uses "min" (not "m") so it cannot be confused with meters when both
 * appear in the same list (e.g. swimming blocks mixing duration and distance).
 */
export function formatBlockSize(block: WorkoutBlock): string {
  const sec = block.durationSeconds ?? 0;
  if (sec > 0) {
    if (sec < 60) return `${sec}s`;
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}min ${s}s` : `${m}min`;
  }
  const meters = block.distanceMeters ?? 0;
  if (meters > 0) {
    if (meters >= 1000) {
      const km = meters / 1000;
      return `${Number.isInteger(km) ? km : km.toFixed(1)}km`;
    }
    return `${meters}m`;
  }
  return '0s';
}
