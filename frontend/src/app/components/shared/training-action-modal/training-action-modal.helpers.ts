import {TranslateService} from '@ngx-translate/core';
import {ClubGroup, GroupLinkedTraining} from '../../../services/club.service';
import {User} from '../../../services/auth.service';
import {TrainingActionMode} from './training-action-mode.type';

export function getModalTitle(mode: TrainingActionMode, translate: TranslateService): string {
  switch (mode) {
    case 'session': return translate.instant('TRAINING_ACTION.MODAL_TITLE_LINK_TRAINING');
    case 'self-schedule': return translate.instant('TRAINING_ACTION.MODAL_TITLE_SCHEDULE_WORKOUT');
    case 'coach-assign':
    case 'group-assign': return translate.instant('TRAINING_ACTION.MODAL_TITLE_ASSIGN_WORKOUT');
  }
}

export function getSubmitLabel(
  mode: TrainingActionMode,
  tab: 'ai' | 'select',
  selectedAthleteCount: number,
  translate: TranslateService,
): string {
  if (tab === 'ai') return translate.instant('TRAINING_ACTION.SUBMIT_GENERATE');
  switch (mode) {
    case 'session': return translate.instant('TRAINING_ACTION.SUBMIT_LINK_TRAINING');
    case 'self-schedule': return translate.instant('TRAINING_ACTION.SUBMIT_SCHEDULE');
    case 'coach-assign': return translate.instant('TRAINING_ACTION.SUBMIT_ASSIGN');
    case 'group-assign':
      return translate.instant('TRAINING_ACTION.SUBMIT_ASSIGN_TO_N_ATHLETES', {count: selectedAthleteCount});
  }
}

export function getLoadingLabel(mode: TrainingActionMode, tab: 'ai' | 'select', translate: TranslateService): string {
  if (tab === 'ai') return translate.instant('TRAINING_ACTION.LOADING_GENERATING');
  switch (mode) {
    case 'session': return translate.instant('TRAINING_ACTION.LOADING_LINKING');
    case 'self-schedule': return translate.instant('TRAINING_ACTION.LOADING_SCHEDULING');
    case 'coach-assign':
    case 'group-assign': return translate.instant('TRAINING_ACTION.LOADING_ASSIGNING');
  }
}

/** Groups still available for linking (excludes already-linked groups). */
export function sessionAvailableGroups(
  mode: TrainingActionMode,
  availableGroups: ClubGroup[],
  existingLinkedTrainings: GroupLinkedTraining[],
  sessionGroupId?: string,
): ClubGroup[] {
  if (mode !== 'session') return availableGroups;
  if (sessionGroupId) return [];
  if (existingLinkedTrainings.some(glt => !glt.clubGroupId)) return [];
  const linkedGroupIds = new Set(existingLinkedTrainings.filter(g => g.clubGroupId).map(g => g.clubGroupId));
  return availableGroups.filter(g => !linkedGroupIds.has(g.id));
}

export function sessionShowNoGroupOption(mode: TrainingActionMode, existingLinkedTrainings: GroupLinkedTraining[]): boolean {
  if (mode !== 'session') return true;
  return !existingLinkedTrainings.some(glt => !!glt.clubGroupId);
}

/** False when all link slots are taken. */
export function canLinkTraining(
  mode: TrainingActionMode,
  availableGroups: ClubGroup[],
  existingLinkedTrainings: GroupLinkedTraining[],
  sessionGroupId?: string,
): boolean {
  if (mode !== 'session') return true;
  if (existingLinkedTrainings.some(glt => !glt.clubGroupId)) return false;
  if (availableGroups.length === 0) return existingLinkedTrainings.length === 0;
  return (
    sessionAvailableGroups(mode, availableGroups, existingLinkedTrainings, sessionGroupId).length > 0 ||
    sessionShowNoGroupOption(mode, existingLinkedTrainings)
  );
}

/** Toggles a tag in the active set; also adjusts the selected athletes accordingly. */
export function toggleTagSelection(
  tag: string,
  activeTags: Set<string>,
  availableAthletes: User[],
  selectedAthleteIds: string[],
): string[] {
  const taggedIds = availableAthletes.filter(a => a.groups?.includes(tag)).map(a => a.id);
  if (activeTags.has(tag)) {
    activeTags.delete(tag);
    return selectedAthleteIds.filter(id => !taggedIds.includes(id));
  }
  activeTags.add(tag);
  const next = [...selectedAthleteIds];
  for (const id of taggedIds) {
    if (!next.includes(id)) next.push(id);
  }
  return next;
}

export function enrichAthletesWithGroups(athletes: User[], groups: ClubGroup[]): void {
  if (!groups.length || !athletes.length) return;
  for (const athlete of athletes) {
    athlete.groups = groups.filter(g => g.memberIds.includes(athlete.id)).map(g => g.name);
  }
}
