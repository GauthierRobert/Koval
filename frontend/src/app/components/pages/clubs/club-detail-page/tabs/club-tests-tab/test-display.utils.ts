import {
  RankingMetric,
  ReferenceTarget,
  SegmentResultUnit,
  SportType,
} from '../../../../../../services/club-test.service';

export function sportLabelKey(sport: SportType | string): string {
  return `CLUB_TESTS.SPORT_${sport}`;
}

export function unitLabelKey(unit: SegmentResultUnit | string): string {
  return `CLUB_TESTS.UNIT_${unit}`;
}

export function targetLabelKey(target: ReferenceTarget | string): string {
  return `CLUB_TESTS.TARGET_${target}`;
}

export function metricLabelKey(metric: RankingMetric | string): string {
  return `CLUB_TESTS.METRIC_${metric}`;
}
