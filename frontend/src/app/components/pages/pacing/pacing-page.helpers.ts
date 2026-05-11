import {TranslateService} from '@ngx-translate/core';
import {Race} from '../../../services/race.service';
import {PacingPlanResponse, PacingSegment, RouteCoordinate} from '../../../services/pacing.service';

export function terrainLabel(gradient: number, translate: TranslateService): string {
  if (gradient > 6) return translate.instant('PACING.TERRAIN_STEEP_CLIMB');
  if (gradient > 3) return translate.instant('PACING.TERRAIN_CLIMB');
  if (gradient > 1) return translate.instant('PACING.TERRAIN_SLIGHT_CLIMB');
  if (gradient >= -1) return translate.instant('PACING.TERRAIN_FLAT');
  if (gradient >= -3) return translate.instant('PACING.TERRAIN_SLIGHT_DESCENT');
  if (gradient >= -6) return translate.instant('PACING.TERRAIN_DESCENT');
  return translate.instant('PACING.TERRAIN_STEEP_DESCENT');
}

export function formatPacingTime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.round(seconds % 60);
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export function formatPacingDistance(meters: number): string {
  if (meters >= 1000) return (meters / 1000).toFixed(1) + ' km';
  return Math.round(meters) + ' m';
}

export interface GenerateBlockedInput {
  discipline: string;
  linkedRace: Race | null;
  bikeGpxFile: File | null;
  runGpxFile: File | null;
  gpxFile: File | null;
}

export function generateBlockedReason(input: GenerateBlockedInput, translate: TranslateService): string | null {
  const {discipline, linkedRace, bikeGpxFile, runGpxFile, gpxFile} = input;

  if (linkedRace) {
    if (discipline === 'SWIM') return null;
    if (discipline === 'TRIATHLON') {
      const missing: string[] = [];
      if (!linkedRace.hasBikeGpx) missing.push(translate.instant('PACING.GPX_INDICATOR_BIKE'));
      if (!linkedRace.hasRunGpx) missing.push(translate.instant('PACING.GPX_INDICATOR_RUN'));
      return missing.length
        ? translate.instant('PACING.BLOCKED_RACE_MISSING_MULTI', {missing: missing.join(' and ')})
        : null;
    }
    if (discipline === 'BIKE') {
      return linkedRace.hasBikeGpx ? null : translate.instant('PACING.BLOCKED_RACE_MISSING_BIKE');
    }
    if (discipline === 'RUN') {
      return linkedRace.hasRunGpx ? null : translate.instant('PACING.BLOCKED_RACE_MISSING_RUN');
    }
  }
  if (discipline === 'SWIM') return null;
  if (discipline === 'TRIATHLON') {
    const missing: string[] = [];
    if (!bikeGpxFile) missing.push(translate.instant('PACING.GPX_INDICATOR_BIKE'));
    if (!runGpxFile) missing.push(translate.instant('PACING.GPX_INDICATOR_RUN'));
    return missing.length
      ? translate.instant('PACING.ERROR_UPLOAD_BIKE_RUN', {missing: missing.join(' and ')})
      : null;
  }
  return gpxFile ? null : translate.instant('PACING.ERROR_UPLOAD_REQUIRED');
}

export function disciplineFromRaceSport(sport: string | undefined): string | null {
  switch (sport) {
    case 'TRIATHLON': return 'TRIATHLON';
    case 'CYCLING': return 'BIKE';
    case 'RUNNING': return 'RUN';
    case 'SWIMMING': return 'SWIM';
    default: return null;
  }
}

export function availableTabsForPlan(plan: PacingPlanResponse): string[] {
  const tabs: string[] = [];
  if (plan.swimSummary) tabs.push('SWIM');
  if (plan.bikeSegments?.length) tabs.push('BIKE');
  if (plan.runSegments?.length) tabs.push('RUN');
  return tabs;
}

export function activeSegments(plan: PacingPlanResponse, activeTab: string): PacingSegment[] {
  if (activeTab === 'BIKE' && plan.bikeSegments?.length) return plan.bikeSegments;
  if (activeTab === 'RUN' && plan.runSegments?.length) return plan.runSegments;
  return plan.bikeSegments || plan.runSegments || [];
}

export function activeRouteCoordinates(plan: PacingPlanResponse, activeTab: string): RouteCoordinate[] | null {
  if (activeTab === 'RUN' && plan.runRouteCoordinates?.length) return plan.runRouteCoordinates;
  if (plan.bikeRouteCoordinates?.length) return plan.bikeRouteCoordinates;
  return plan.runRouteCoordinates;
}
