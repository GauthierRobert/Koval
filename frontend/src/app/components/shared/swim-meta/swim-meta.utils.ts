import { TranslateService } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../models/training.model';
import { formatTimeMS } from '../format/format.utils';

export function isSwim(sport: string | null | undefined): boolean {
  return sport === 'SWIMMING';
}

export function hasSwimMeta(block: WorkoutBlock | null | undefined): boolean {
  if (!block) return false;
  return !!(
    block.strokeType ||
    (block.equipment && block.equipment.length) ||
    block.sendOffSeconds
  );
}

/** Send-off as "M:SS"; empty string when missing/zero (so callers can skip rendering). */
export function formatSendOff(seconds: number | undefined | null): string {
  if (!seconds || seconds <= 0) return '';
  return formatTimeMS(seconds);
}

/** Plain-text tooltip for a swim block — stroke · send-off · equipment. */
export function swimMetaTooltip(
  block: WorkoutBlock | null | undefined,
  translate: TranslateService,
  sport?: string | null,
): string {
  if (sport !== undefined && !isSwim(sport)) return '';
  if (!hasSwimMeta(block)) return '';
  const parts: string[] = [];
  if (block!.strokeType) {
    parts.push(translate.instant('STROKE.' + block!.strokeType));
  }
  if (block!.sendOffSeconds) {
    parts.push(
      `${translate.instant('WORKOUT_DETAIL.LABEL_SENDOFF')} ${formatSendOff(block!.sendOffSeconds)}`,
    );
  }
  if (block!.equipment?.length) {
    parts.push(
      block!.equipment.map((e) => translate.instant('SWIM_EQUIPMENT.' + e)).join(', '),
    );
  }
  return parts.join(' · ');
}
