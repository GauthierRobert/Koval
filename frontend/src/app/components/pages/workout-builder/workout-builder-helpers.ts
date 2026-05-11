import {isSet, StrokeType, SwimEquipment, TrainingType, WorkoutBlock} from '../../../models/training.model';

type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
type BlockType = WorkoutBlock['type'];

export interface BlockEditFormState {
  editType: BlockType;
  editLabel: string;
  editDurationMin: number;
  editDurationSec: number;
  editDistanceMeters: number | null;
  editIntensity: number | null;
  editIntensityStart: number | null;
  editIntensityEnd: number | null;
  editCadence: number | null;
  editZone: string;
  editStrokeType: StrokeType | null;
  editEquipment: SwimEquipment[];
  editSendOffSeconds: number | null;
}

/** Total TSS = Σ duration_seconds × (intensity/100)² / 36 (work + intra-set rest). */
export function computeBlockTss(blocks: WorkoutBlock[]): number {
  let tss = 0;
  for (const block of blocks) {
    if (isSet(block)) {
      const reps = block.repetitions ?? 1;
      const childTss = computeBlockTss(block.elements!);
      const restTss = (block.restDurationSeconds ?? 0) * Math.pow((block.restIntensity ?? 60) / 100, 2) / 36;
      tss += reps * childTss + (reps - 1) * restTss;
    } else {
      const dur = block.durationSeconds ?? 0;
      let pct = 50;
      if (block.type === 'RAMP') {
        pct = ((block.intensityStart ?? 50) + (block.intensityEnd ?? 50)) / 2;
      } else if (block.type !== 'FREE' && block.type !== 'PAUSE') {
        pct = block.intensityTarget ?? 50;
      }
      tss += dur * Math.pow(pct / 100, 2) / 36;
    }
  }
  return Math.round(tss);
}

export function computeBlockDuration(blocks: WorkoutBlock[]): number {
  let total = 0;
  for (const block of blocks) {
    if (isSet(block)) {
      const reps = block.repetitions ?? 1;
      const childDur = computeBlockDuration(block.elements!);
      total += reps * childDur + (reps - 1) * (block.restDurationSeconds ?? 0);
    } else {
      total += block.durationSeconds ?? 0;
    }
  }
  return total;
}

function intensityFields(form: BlockEditFormState): Partial<WorkoutBlock> {
  if (form.editType === 'RAMP') {
    return {
      intensityStart: form.editIntensityStart ?? undefined,
      intensityEnd: form.editIntensityEnd ?? undefined,
      intensityTarget: undefined,
    };
  }
  if (form.editType !== 'FREE' && form.editType !== 'PAUSE') {
    return {
      intensityTarget: form.editIntensity ?? undefined,
      intensityStart: undefined,
      intensityEnd: undefined,
    };
  }
  return {intensityTarget: undefined, intensityStart: undefined, intensityEnd: undefined};
}

function swimFields(form: BlockEditFormState, sportType: SportType): Partial<WorkoutBlock> {
  if (sportType !== 'SWIMMING') {
    return {strokeType: undefined, equipment: undefined, sendOffSeconds: undefined};
  }
  return {
    strokeType: form.editStrokeType ?? undefined,
    equipment: form.editEquipment.length ? [...form.editEquipment] : undefined,
    sendOffSeconds: form.editSendOffSeconds ?? undefined,
  };
}

/** Build a new block from the current form state. */
export function buildBlockFromForm(form: BlockEditFormState, sportType: SportType): WorkoutBlock {
  return {
    type: form.editType,
    label: form.editLabel || form.editType,
    durationSeconds: (form.editDurationMin || 0) * 60 + (form.editDurationSec || 0),
    ...(form.editDistanceMeters ? {distanceMeters: form.editDistanceMeters} : {}),
    ...intensityFields(form),
    ...(form.editCadence ? {cadenceTarget: form.editCadence} : {}),
    ...(form.editZone ? {zoneTarget: form.editZone} : {}),
    ...swimFields(form, sportType),
  };
}

/** Produce an updated block by merging the form state onto an existing block. */
export function mergeFormIntoBlock(
  existing: WorkoutBlock,
  form: BlockEditFormState,
  sportType: SportType,
): WorkoutBlock {
  return {
    ...existing,
    type: form.editType,
    label: form.editLabel || form.editType,
    durationSeconds: (form.editDurationMin || 0) * 60 + (form.editDurationSec || 0),
    distanceMeters: form.editDistanceMeters ?? undefined,
    ...intensityFields(form),
    cadenceTarget: form.editCadence ?? undefined,
    zoneTarget: form.editZone || undefined,
    ...swimFields(form, sportType),
  };
}

export function blockToFormState(block: WorkoutBlock): BlockEditFormState {
  const dur = block.durationSeconds || 0;
  return {
    editType: block.type,
    editLabel: block.label || '',
    editDurationMin: Math.floor(dur / 60),
    editDurationSec: dur % 60,
    editDistanceMeters: block.distanceMeters ?? null,
    editIntensity: block.intensityTarget ?? null,
    editIntensityStart: block.intensityStart ?? null,
    editIntensityEnd: block.intensityEnd ?? null,
    editCadence: block.cadenceTarget ?? null,
    editZone: block.zoneTarget || '',
    editStrokeType: block.strokeType ?? null,
    editEquipment: block.equipment ? [...block.equipment] : [],
    editSendOffSeconds: block.sendOffSeconds ?? null,
  };
}

export function defaultFormState(): BlockEditFormState {
  return {
    editType: 'STEADY',
    editLabel: '',
    editDurationMin: 5,
    editDurationSec: 0,
    editDistanceMeters: null,
    editIntensity: 75,
    editIntensityStart: null,
    editIntensityEnd: null,
    editCadence: null,
    editZone: '',
    editStrokeType: null,
    editEquipment: [],
    editSendOffSeconds: null,
  };
}

export interface SetEditState {
  editSetReps: number;
  editSetNoRest: boolean;
  editSetRestDuration: number;
  editSetPassiveRest: boolean;
  editSetRestIntensity: number;
  editLabel: string;
}

export function blockToSetEditState(block: WorkoutBlock): SetEditState {
  const passiveRest = (block.restIntensity ?? 60) === 0;
  return {
    editSetReps: block.repetitions ?? 3,
    editSetNoRest: (block.restDurationSeconds ?? 0) === 0,
    editSetRestDuration: block.restDurationSeconds ?? 120,
    editSetPassiveRest: passiveRest,
    editSetRestIntensity: passiveRest ? 60 : (block.restIntensity ?? 60),
    editLabel: block.label || '',
  };
}

export function applySetEditToBlock(block: WorkoutBlock, state: SetEditState): WorkoutBlock {
  return {
    ...block,
    label: state.editLabel || `${state.editSetReps}x Set`,
    repetitions: state.editSetReps,
    restDurationSeconds: state.editSetNoRest ? 0 : state.editSetRestDuration,
    restIntensity: state.editSetNoRest ? 0 : state.editSetPassiveRest ? 0 : state.editSetRestIntensity,
  };
}

/** Builds a new "set" block from the given child blocks. */
export function createSetBlock(
  children: WorkoutBlock[],
  reps: number,
  noRest: boolean,
  restDuration: number,
  restIntensity: number,
  passiveRest: boolean,
): WorkoutBlock {
  return {
    type: 'INTERVAL',
    label: `${reps}x Set`,
    repetitions: reps,
    elements: children,
    restDurationSeconds: noRest ? 0 : restDuration,
    restIntensity: noRest ? 0 : passiveRest ? 0 : restIntensity,
  };
}

/** Removes one element from a parent set; if no elements remain, returns null to signal "drop parent". */
export function removeChildFromSet(parent: WorkoutBlock, childIndex: number): WorkoutBlock | null {
  if (!parent.elements) return parent;
  const elements = parent.elements.filter((_, i) => i !== childIndex);
  return elements.length === 0 ? null : {...parent, elements};
}

export function formatBlockSeconds(sec: number): string {
  if (sec < 60) return `${sec}s`;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return s > 0 ? `${m}min ${s}s` : `${m}min`;
}

export function formatTotalDuration(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  return h > 0 ? `${h}h ${m}min` : `${m}min`;
}

export interface TrainingSavePayload {
  title: string;
  description: string;
  sportType: SportType;
  trainingType: TrainingType;
  blocks: WorkoutBlock[];
  estimatedTss: number;
  estimatedDurationSeconds: number;
}

export function buildTrainingSavePayload(input: {
  title: string;
  description: string;
  sportType: SportType;
  trainingType: TrainingType;
  blocks: WorkoutBlock[];
}): TrainingSavePayload {
  return {
    title: input.title.trim(),
    description: input.description.trim(),
    sportType: input.sportType,
    trainingType: input.trainingType,
    blocks: input.blocks,
    estimatedTss: computeBlockTss(input.blocks),
    estimatedDurationSeconds: computeBlockDuration(input.blocks),
  };
}
