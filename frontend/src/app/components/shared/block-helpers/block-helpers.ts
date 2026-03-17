import {Training, WorkoutBlock} from '../../../models/training.model';

/**
 * Pure utility functions for computing workout block visual properties.
 * These functions have no service dependencies and can be reused across components.
 */

export function getMaxIntensity(training: Training): number {
  if (!training.blocks) return 150;
  const intensities = training.blocks.flatMap((b) => [
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

export function getBlockColor(block: WorkoutBlock): string {
  if (block.type === 'PAUSE') return '#636e72';
  if (block.type === 'FREE') return '#636e72';
  if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
  if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';
  const intensity =
    block.type === 'RAMP'
      ? ((block.intensityStart || 0) + (block.intensityEnd || 0)) / 2
      : block.intensityTarget || 0;
  if (intensity < 55) return '#b2bec3';
  if (intensity < 75) return '#3498db';
  if (intensity < 90) return '#2ecc71';
  if (intensity < 105) return '#f1c40f';
  if (intensity < 120) return '#e67e22';
  return '#e74c3c';
}

export function getDisplayIntensity(block: WorkoutBlock): string {
  if (block.type === 'PAUSE') return 'PAUSE';
  if (block.type === 'FREE') return 'FREE';
  if (block.type === 'RAMP') return `${block.intensityStart}%-${block.intensityEnd}%`;
  return `${block.intensityTarget}%`;
}
