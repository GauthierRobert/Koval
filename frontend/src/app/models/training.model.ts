export type SportFilter = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | null;

export const SPORT_OPTIONS: { label: string; value: SportFilter }[] = [
    { label: 'Swim', value: 'SWIMMING' },
    { label: 'Bike', value: 'CYCLING' },
    { label: 'Run', value: 'RUNNING' },
    { label: 'Brick', value: 'BRICK' },
];

export type TrainingType =
    | 'VO2MAX'
    | 'THRESHOLD'
    | 'SWEET_SPOT'
    | 'ENDURANCE'
    | 'SPRINT'
    | 'RECOVERY'
    | 'MIXED'
    | 'TEST';

export type TrainingVisibility = 'PUBLIC' | 'PRIVATE' | 'COACH_ONLY';

export const TRAINING_TYPES: TrainingType[] = [
    'VO2MAX',
    'THRESHOLD',
    'SWEET_SPOT',
    'ENDURANCE',
    'SPRINT',
    'RECOVERY',
    'MIXED',
    'TEST',
];

export const TRAINING_TYPE_COLORS: Record<TrainingType, string> = {
    VO2MAX: '#ef4444',
    THRESHOLD: '#f97316',
    SWEET_SPOT: '#eab308',
    ENDURANCE: '#22c55e',
    SPRINT: '#a855f7',
    RECOVERY: '#06b6d4',
    MIXED: '#6366f1',
    TEST: '#ec4899',
};

export const TRAINING_TYPE_LABELS: Record<TrainingType, string> = {
    VO2MAX: 'VO2max',
    THRESHOLD: 'Threshold',
    SWEET_SPOT: 'Sweet Spot',
    ENDURANCE: 'Endurance',
    SPRINT: 'Sprint',
    RECOVERY: 'Recovery',
    MIXED: 'Mixed',
    TEST: 'Test',
};

export interface WorkoutBlock {
    // Set fields (non-null when this is a repeatable group)
    repetitions?: number;
    elements?: WorkoutBlock[];
    restDurationSeconds?: number;
    restIntensity?: number;

    // Leaf fields (single block)
    type: 'WARMUP' | 'STEADY' | 'INTERVAL' | 'COOLDOWN' | 'RAMP' | 'FREE' | 'PAUSE';
    durationSeconds?: number;
    distanceMeters?: number;
    intensityTarget?: number;
    intensityStart?: number;
    intensityEnd?: number;
    cadenceTarget?: number;
    zoneTarget?: string;
    zoneSystemId?: string;
    label: string;
    description?: string;
    zoneLabel?: string;
}

/** Alias for clarity — WorkoutBlock is now a recursive WorkoutElement. */
export type WorkoutElement = WorkoutBlock;

/** Returns true when the block is a set (has children). */
export function isSet(block: WorkoutBlock): boolean {
    return !!block.elements && block.elements.length > 0;
}

/** Flattens a tree of WorkoutElements into a sequential list of leaf blocks. */
export function flattenElements(elements: WorkoutBlock[]): WorkoutBlock[] {
    if (!elements || elements.length === 0) return [];
    const result: WorkoutBlock[] = [];
    for (const element of elements) {
        flattenElement(element, result);
    }
    return result;
}

function flattenElement(element: WorkoutBlock, result: WorkoutBlock[]): void {
    if (!isSet(element)) {
        result.push(element);
        return;
    }
    const reps = element.repetitions ?? 1;
    const flatChildren = flattenElements(element.elements!);
    for (let i = 0; i < reps; i++) {
        result.push(...flatChildren);
        // Insert rest between reps (not after the last one)
        if (i < reps - 1 && element.restDurationSeconds && element.restDurationSeconds > 0) {
            const intensity = element.restIntensity ?? 60;
            result.push({
                type: intensity > 0 ? 'STEADY' : 'PAUSE',
                durationSeconds: element.restDurationSeconds,
                intensityTarget: intensity,
                label: intensity > 0 ? 'Active Rest' : 'Rest',
            });
        }
    }
}

export interface Training {
    id: string;
    title: string;
    description: string;
    blocks?: WorkoutBlock[];
    sportType: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
    trainingType?: TrainingType;
    groupIds?: string[];
    visibility?: TrainingVisibility;
    createdBy?: string;
    estimatedTss?: number;
    estimatedIf?: number;
    estimatedDurationSeconds?: number;
    estimatedDistance?: number;
    zoneSystemId?: string;
    createdAt?: string;
    clubIds?: string[];
    clubGroupIds?: string[];
    _receivedMeta?: {
        assignedByName?: string;
        origin: 'COACH_GROUP' | 'CLUB' | 'CLUB_SESSION';
        originName?: string;
    };
}

export interface ReceivedTraining {
    id: string;
    trainingId: string;
    assignedByName?: string;
    origin: 'COACH_GROUP' | 'CLUB' | 'CLUB_SESSION';
    originName?: string;
    receivedAt: string;
}

/** Returns true when at least one block is distance-based (no explicit durationSeconds). */
export function hasDurationEstimate(training: Training): boolean {
    return flattenElements(training.blocks ?? []).some(
        (b) => (b.distanceMeters ?? 0) > 0 && !((b.durationSeconds ?? 0) > 0)
    );
}
