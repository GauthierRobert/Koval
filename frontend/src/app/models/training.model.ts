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
    return (training.blocks ?? []).some(
        (b) => (b.distanceMeters ?? 0) > 0 && !((b.durationSeconds ?? 0) > 0)
    );
}
