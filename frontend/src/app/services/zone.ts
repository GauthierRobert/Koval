export type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING';

export type ZoneReferenceType =
  | 'FTP'
  | 'VO2MAX_POWER'
  | 'THRESHOLD_PACE'
  | 'VO2MAX_PACE'
  | 'CSS'
  | 'PACE_5K'
  | 'PACE_10K'
  | 'PACE_HALF_MARATHON'
  | 'PACE_MARATHON'
  | 'CUSTOM';

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
  referenceType: ZoneReferenceType;
  referenceName?: String;
  zones: Zone[];
  createdAt?: string;
  updatedAt?: string;
}
