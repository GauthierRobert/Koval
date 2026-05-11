import {PacingSegment} from '../../../services/pacing.service';

export interface NutritionEvent {
  distance: number;
  suggestion: string;
}

export interface RacePlanGroup {
  index: number;
  segmentStart: number;
  segmentEnd: number;
  startDistance: number;
  endDistance: number;
  targetPower?: number;
  powerLower?: number;
  powerUpper?: number;
  targetPace?: string;
  meanGradient: number;
  elevationChange: number;
  totalTime: number;
  meanSpeed?: number;
  fatigueStart: number;
  fatigueEnd: number;
  nutritionEvents: NutritionEvent[];
  terrainLabel: string;
}

export type TerrainLabeler = (gradient: number) => string;

/** Group consecutive segments by exact pace (run) or by ±bandW power band (bike). */
export function groupPacingSegments(
  segments: PacingSegment[],
  bandW: number,
  terrainLabeler: TerrainLabeler,
): RacePlanGroup[] {
  const groups: RacePlanGroup[] = [];
  if (!segments.length) return groups;

  let groupStart = 0;
  const firstSeg = segments[0];
  let groupWeightedPower = (firstSeg.targetPower ?? 0) * firstSeg.estimatedSegmentTime;
  let groupTotalTime = firstSeg.estimatedSegmentTime;

  for (let i = 1; i <= segments.length; i++) {
    const prev = segments[i - 1];
    const curr = i < segments.length ? segments[i] : null;

    let changed: boolean;
    if (!curr) {
      changed = true;
    } else if (prev.targetPace != null) {
      changed = curr.targetPace !== prev.targetPace;
    } else if (prev.targetPower != null && curr.targetPower != null) {
      const currentMean = groupTotalTime > 0 ? groupWeightedPower / groupTotalTime : prev.targetPower;
      changed = Math.abs(curr.targetPower - currentMean) > bandW;
    } else {
      changed = true;
    }

    if (!changed) {
      groupWeightedPower += (curr!.targetPower ?? 0) * curr!.estimatedSegmentTime;
      groupTotalTime += curr!.estimatedSegmentTime;
    }

    if (changed) {
      const groupSegments = segments.slice(groupStart, i);
      const first = groupSegments[0];
      const last = groupSegments[groupSegments.length - 1];

      let totalDist = 0;
      let weightedGradient = 0;
      let totalTime = 0;
      const nutritionEvents: NutritionEvent[] = [];

      for (const seg of groupSegments) {
        const segDist = seg.endDistance - seg.startDistance;
        totalDist += segDist;
        weightedGradient += seg.gradient * segDist;
        totalTime += seg.estimatedSegmentTime;
        if (seg.nutritionSuggestion) {
          nutritionEvents.push({
            distance: (seg.startDistance + seg.endDistance) / 2,
            suggestion: seg.nutritionSuggestion,
          });
        }
      }

      const meanGradient = totalDist > 0 ? weightedGradient / totalDist : 0;
      const elevationChange = Math.round(last.elevation - first.elevation);
      const meanSpeed = totalTime > 0 ? (totalDist / 1000) / (totalTime / 3600) : undefined;
      const groupMeanPower = first.targetPower != null && groupTotalTime > 0
        ? Math.round(groupWeightedPower / groupTotalTime)
        : first.targetPower;

      groups.push({
        index: groups.length + 1,
        segmentStart: groupStart,
        segmentEnd: i - 1,
        startDistance: first.startDistance,
        endDistance: last.endDistance,
        targetPower: groupMeanPower,
        powerLower: groupMeanPower != null ? groupMeanPower - bandW : undefined,
        powerUpper: groupMeanPower != null ? groupMeanPower + bandW : undefined,
        targetPace: first.targetPace,
        meanGradient,
        elevationChange,
        totalTime,
        meanSpeed,
        fatigueStart: first.cumulativeFatigue,
        fatigueEnd: last.cumulativeFatigue,
        nutritionEvents,
        terrainLabel: terrainLabeler(meanGradient),
      });

      groupStart = i;
      if (curr) {
        groupWeightedPower = curr.targetPower != null ? curr.targetPower * curr.estimatedSegmentTime : 0;
        groupTotalTime = curr.estimatedSegmentTime;
      } else {
        groupWeightedPower = 0;
        groupTotalTime = 0;
      }
    }
  }

  return groups;
}

export function groupingCacheKey(segments: PacingSegment[], bandW: number): string {
  const first = segments[0];
  const last = segments[segments.length - 1];
  return (
    segments.length +
    ':' + (first?.startDistance ?? '') +
    ':' + (last?.endDistance ?? '') +
    ':' + bandW +
    ':' + (first?.targetPower ?? first?.targetPace ?? '') +
    ':' + (last?.targetPower ?? last?.targetPace ?? '')
  );
}
