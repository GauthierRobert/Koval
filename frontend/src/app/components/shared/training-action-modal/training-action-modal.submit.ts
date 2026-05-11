import {Observable} from 'rxjs';
import {NgZone} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ActionContext, AIActionService} from '../../../services/ai-action.service';
import {ClubSessionService} from '../../../services/club-session.service';
import {CalendarService} from '../../../services/calendar.service';
import {CoachService} from '../../../services/coach.service';
import {SportFilter} from '../../../models/training.model';
import {TrainingActionMode} from './training-action-mode.type';

export interface SubmitDeps {
  aiActionService: AIActionService;
  clubSessionService: ClubSessionService;
  calendarService: CalendarService;
  coachService: CoachService;
  translate: TranslateService;
  ngZone: NgZone;
}

export interface SubmitCallbacks {
  setLoading: (loading: boolean) => void;
  setError: (msg: string) => void;
  setSuccess: (msg: string) => void;
  emitCompleted: (payload: {success: boolean; content?: string}) => void;
  close: () => void;
  /** Resets the prompt and switches to the select tab after AI training generation. */
  onAiCreated?: () => void;
}

export interface AiSubmitInput {
  prompt: string;
  mode: TrainingActionMode;
  clubId?: string;
  sessionId?: string;
  groupId?: string;
  selectedGroupId: string;
  selectedSport: SportFilter;
  selectedZoneSystemId: string;
}

export function submitAi(deps: SubmitDeps, cb: SubmitCallbacks, input: AiSubmitInput): void {
  cb.setLoading(true);
  cb.setError('');
  cb.setSuccess('');

  const trimmed = input.prompt.trim();
  if (input.mode === 'session') {
    const ctx: ActionContext = {
      clubId: input.clubId,
      sessionId: input.sessionId,
      clubGroupId: input.selectedGroupId || undefined,
      sport: input.selectedSport || undefined,
      zoneSystemId: input.selectedZoneSystemId || undefined,
    };
    deps.aiActionService.executeAction(trimmed, 'TRAINING_WITH_SESSION', ctx).subscribe({
      next: (result) => deps.ngZone.run(() => {
        cb.setLoading(false);
        if (result.success) {
          cb.setSuccess(result.content);
          cb.emitCompleted({success: true, content: result.content});
        } else {
          cb.setError(result.content);
        }
      }),
      error: (err) => deps.ngZone.run(() => {
        cb.setLoading(false);
        cb.setError(err?.error?.message ?? deps.translate.instant('TRAINING_ACTION.ERROR_UNEXPECTED'));
      }),
    });
    return;
  }

  const ctx: ActionContext = {
    sport: input.selectedSport || undefined,
    zoneSystemId: input.selectedZoneSystemId || undefined,
    clubId: input.clubId,
    clubGroupId: input.selectedGroupId || undefined,
    coachGroupId: input.groupId,
  };
  deps.aiActionService.executeAction(trimmed, 'TRAINING_CREATION', ctx).subscribe({
    next: () => deps.ngZone.run(() => {
      cb.setLoading(false);
      cb.setSuccess(deps.translate.instant('TRAINING_ACTION.SUCCESS_TRAINING_CREATED'));
      cb.onAiCreated?.();
    }),
    error: (err) => deps.ngZone.run(() => {
      cb.setLoading(false);
      cb.setError(err?.error?.message ?? deps.translate.instant('TRAINING_ACTION.ERROR_FAILED_GENERATE'));
    }),
  });
}

function runSubmit<T>(
  deps: SubmitDeps,
  cb: SubmitCallbacks,
  source$: Observable<T>,
  errorKey: string,
  onSuccess: (value: T) => void,
): void {
  source$.subscribe({
    next: (value) => deps.ngZone.run(() => {
      cb.setLoading(false);
      onSuccess(value);
    }),
    error: (err) => deps.ngZone.run(() => {
      cb.setLoading(false);
      cb.setError(err?.error?.message ?? deps.translate.instant(errorKey));
    }),
  });
}

export interface SelectSubmitInput {
  mode: TrainingActionMode;
  clubId?: string;
  groupId?: string;
  sessionId?: string;
  selectedTrainingId: string;
  selectedGroupId: string;
  selectedDate: string;
  notes: string;
  preselectedAthleteIds: string[];
  selectedAthleteIds: string[];
}

export function submitSelect(deps: SubmitDeps, cb: SubmitCallbacks, input: SelectSubmitInput): void {
  cb.setLoading(true);
  cb.setError('');
  cb.setSuccess('');

  const completeAndClose = () => {
    cb.emitCompleted({success: true});
    cb.close();
  };

  switch (input.mode) {
    case 'session':
      if (!input.clubId || !input.sessionId) return;
      runSubmit(
        deps,
        cb,
        deps.clubSessionService.linkTrainingToSession(
          input.clubId,
          input.sessionId,
          input.selectedTrainingId,
          input.selectedGroupId || undefined,
        ),
        'TRAINING_ACTION.ERROR_FAILED_LINK',
        () => {
          cb.setSuccess(deps.translate.instant('TRAINING_ACTION.SUCCESS_TRAINING_LINKED'));
          cb.emitCompleted({success: true, content: 'Training linked.'});
        },
      );
      return;

    case 'self-schedule':
      runSubmit(
        deps,
        cb,
        deps.calendarService.scheduleWorkout(input.selectedTrainingId, input.selectedDate, input.notes || undefined),
        'TRAINING_ACTION.ERROR_FAILED_SCHEDULE',
        completeAndClose,
      );
      return;

    case 'coach-assign':
      if (input.preselectedAthleteIds.length === 0) return;
      runSubmit(
        deps,
        cb,
        deps.coachService.assignTraining(
          input.selectedTrainingId,
          input.preselectedAthleteIds,
          input.selectedDate,
          input.notes || undefined,
        ),
        'TRAINING_ACTION.ERROR_FAILED_ASSIGN',
        completeAndClose,
      );
      return;

    case 'group-assign':
      if (input.selectedAthleteIds.length === 0) return;
      runSubmit(
        deps,
        cb,
        deps.coachService.assignTraining(
          input.selectedTrainingId,
          input.selectedAthleteIds,
          input.selectedDate,
          input.notes || undefined,
          input.clubId,
          input.groupId,
        ),
        'TRAINING_ACTION.ERROR_FAILED_ASSIGN',
        completeAndClose,
      );
  }
}
