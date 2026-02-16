export type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING';

export type ZoneReferenceType = 
    | 'FTP' 
    | 'THRESHOLD_PACE' 
    | 'CSS' 
    | 'PACE_5K' 
    | 'PACE_10K' 
    | 'PACE_HALF_MARATHON' 
    | 'PACE_MARATHON';

export interface Zone {
    label: string;
    low: number;
    high: number;
    description?: string;
}

export interface ZoneSystem {
    id?: string;
    name: string;
    coachId: string;
    sportType: SportType;
    referenceType?: ZoneReferenceType;
    zones: Zone[];
    isActive: boolean;
    isDefault: boolean;
    createdAt?: string;
    updatedAt?: string;
}
