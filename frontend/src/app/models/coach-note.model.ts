import {Provenance} from './ai-analysis.model';

export interface CoachNote {
    id: string;
    coachId: string;
    athleteId: string;
    sessionId?: string | null;
    body: string;
    provenance: Provenance;
    createdAt: string;
    updatedAt: string;
}
