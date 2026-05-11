import {Training, TrainingType, TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, SPORT_OPTIONS, WorkoutBlock} from '../../../models/training.model';
import {User} from '../../../services/auth.service';
import {formatPace as sharedFormatPace} from '../format/format.utils';

export function intensityValueFromUser(percent: number | undefined, training: Training, user: User | null): string {
  if (percent === undefined || percent === 0) return '-';

  if (training.sportType === 'CYCLING') {
    if (!user?.ftp) return `${percent}%`;
    return Math.round((percent * user.ftp) / 100).toString() + 'W';
  }
  if (training.sportType === 'RUNNING') {
    if (!user?.functionalThresholdPace) return `${percent}%`;
    const secPerKm = user.functionalThresholdPace / (percent / 100);
    if (!isFinite(secPerKm)) return '-';
    return sharedFormatPace(secPerKm) + '/km';
  }
  if (training.sportType === 'SWIMMING') {
    if (!user?.criticalSwimSpeed) return `${percent}%`;
    const secPer100m = user.criticalSwimSpeed / (percent / 100);
    if (!isFinite(secPer100m)) return '-';
    return sharedFormatPace(secPer100m) + '/100m';
  }
  return percent.toString() + '%';
}

export function sportLabelFor(training: Training | null): string {
  if (!training) return '';
  const opt = SPORT_OPTIONS.find(o => o.value === training.sportType);
  return opt?.label ?? training.sportType;
}

export function sportColorFor(training: Training | null): string {
  if (!training) return 'var(--text-muted)';
  switch (training.sportType) {
    case 'SWIMMING': return '#06b6d4';
    case 'CYCLING': return '#22c55e';
    case 'RUNNING': return '#f97316';
    case 'BRICK': return '#a855f7';
    default: return 'var(--text-muted)';
  }
}

export function sportUnitFor(training: Training | null): string {
  if (!training) return '%';
  if (training.sportType === 'CYCLING') return 'W';
  if (training.sportType === 'RUNNING') return 'min/km';
  if (training.sportType === 'SWIMMING') return 'min/100m';
  return '%';
}

export function trainingTypeLabelFor(training: Training | null): string {
  if (!training?.trainingType) return '';
  return TRAINING_TYPE_LABELS[training.trainingType as TrainingType] ?? training.trainingType;
}

export function trainingTypeColorFor(training: Training | null): string {
  if (!training?.trainingType) return 'var(--accent-color)';
  return TRAINING_TYPE_COLORS[training.trainingType as TrainingType] ?? 'var(--accent-color)';
}

export function blockClipPath(block: WorkoutBlock, maxIntensity: number, getEffective: (b: WorkoutBlock, t: 'START' | 'END') => number): string {
  if (block.type !== 'RAMP') return 'none';

  const startVal = getEffective(block, 'START');
  const endVal = getEffective(block, 'END');

  const startH = (startVal / maxIntensity) * 100;
  const endH = (endVal / maxIntensity) * 100;
  const currentH = Math.max(startH, endH);

  if (currentH === 0) return 'none';
  const startRel = 100 - (startH / currentH) * 100;
  const endRel = 100 - (endH / currentH) * 100;
  return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
}

export function yAxisLabels(maxIntensity: number): number[] {
  const step = maxIntensity > 200 ? 100 : 50;
  const labels: number[] = [];
  for (let i = 0; i <= maxIntensity; i += step) labels.unshift(i);
  return labels;
}

export type IntensityType = 'TARGET' | 'START' | 'END';

export function effectiveIntensity(block: WorkoutBlock, type: IntensityType = 'TARGET'): number {
  if (type === 'START') return block.intensityStart ?? 0;
  if (type === 'END') return block.intensityEnd ?? 0;
  return block.intensityTarget ?? 0;
}

export function blockHeightPercent(block: WorkoutBlock, maxIntensity: number): number {
  if (block.type === 'PAUSE') return 100;
  if (block.type === 'FREE') return (65 / maxIntensity) * 100;
  if (block.type === 'TRANSITION') return (30 / maxIntensity) * 100;
  const intensity =
    block.type === 'RAMP'
      ? Math.max(effectiveIntensity(block, 'START'), effectiveIntensity(block, 'END'))
      : effectiveIntensity(block, 'TARGET');
  return (intensity / maxIntensity) * 100;
}

export function maxFlatIntensity(flatBlocks: WorkoutBlock[]): number {
  const intensities = flatBlocks.flatMap(b => [
    effectiveIntensity(b, 'TARGET'),
    effectiveIntensity(b, 'START'),
    effectiveIntensity(b, 'END'),
  ]);
  const maxBlockIntensity = intensities.length > 0 ? Math.max(...intensities) : 0;
  return Math.max(150, maxBlockIntensity + 20);
}

export function formatBlockDuration(seconds: number | undefined): string {
  if (seconds === undefined) return '0min';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (s === 0) return `${m}min`;
  if (m === 0) return `${s}sec`;
  return `${m}m ${s}sec`;
}

export function formatTotalSeconds(totalSeconds: number): string {
  if (totalSeconds === 0) return '0 min';
  return `${Math.floor(totalSeconds / 60)}m`;
}

export function formatDistanceMeters(meters: number): string {
  const km = meters / 1000;
  return km >= 1 ? `${km >= 10 ? km.toFixed(0) : km.toFixed(1)}km` : `${meters}m`;
}

export interface BlockEstimateDisplay {
  duration: string;
  durationEstimated: boolean;
  distance: string;
  distanceEstimated: boolean;
}

export function blockEstimateDisplay(
  block: WorkoutBlock,
  estimateDuration: () => number,
  estimateDistance: () => number,
): BlockEstimateDisplay {
  const hasDuration = (block.durationSeconds ?? 0) > 0;
  const hasDistance = (block.distanceMeters ?? 0) > 0;

  let duration: string;
  let durationEstimated: boolean;
  if (hasDuration) {
    duration = formatBlockDuration(block.durationSeconds);
    durationEstimated = false;
  } else {
    const est = estimateDuration();
    duration = est > 0 ? formatBlockDuration(est) : '-';
    durationEstimated = est > 0;
  }

  let distance: string;
  let distanceEstimated: boolean;
  if (hasDistance) {
    distance = formatDistanceMeters(block.distanceMeters!);
    distanceEstimated = false;
  } else {
    const est = estimateDistance();
    distance = est > 0 ? formatDistanceMeters(est) : '-';
    distanceEstimated = est > 0;
  }

  return {duration, durationEstimated, distance, distanceEstimated};
}
