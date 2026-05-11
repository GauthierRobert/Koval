import {ClubTrainingSession, GroupLinkedTraining} from '../../../../../../services/club.service';
import {ClubSessionService} from '../../../../../../services/club-session.service';
import {SessionFormSaveEvent} from './session-form-modal/session-form-modal.component';
import {SingleSessionCreateEvent} from './create-single-session-modal/create-single-session-modal.component';
import {RecurringTemplateCreateEvent} from './create-recurring-template-modal/create-recurring-template-modal.component';
import {toRecurringSessionDto, toRecurringSessionDtoFromEdit, toSessionDto} from './session-form-mapper';

/** Triggers a browser download of the session's GPX file. */
export function downloadSessionGpx(
  service: ClubSessionService,
  clubId: string,
  session: ClubTrainingSession,
): void {
  service.downloadSessionGpx(clubId, session.id).subscribe({
    next: (blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = session.gpxFileName ?? 'route.gpx';
      a.click();
      URL.revokeObjectURL(url);
    },
  });
}

/** Shares the session's GPX file via the Web Share API, falling back to download. */
export async function shareSessionGpx(
  service: ClubSessionService,
  clubId: string,
  session: ClubTrainingSession,
): Promise<void> {
  if (!navigator.share) {
    downloadSessionGpx(service, clubId, session);
    return;
  }
  service.downloadSessionGpx(clubId, session.id).subscribe({
    next: async (blob) => {
      const file = new File([blob], session.gpxFileName ?? 'route.gpx', {type: 'application/gpx+xml'});
      try {
        await navigator.share({title: session.title, files: [file]});
      } catch {
        // user cancelled share
      }
    },
  });
}

export interface SessionSaveCallbacks {
  setSaving: (saving: boolean) => void;
  onDone: () => void;
  reload: () => void;
}

/**
 * Edit-form save flow: handles GPX removal, recurring-template edits, and single-session updates.
 */
export function submitEditSession(
  service: ClubSessionService,
  clubId: string,
  event: SessionFormSaveEvent,
  callbacks: SessionSaveCallbacks,
): void {
  if (!event.editingSession) return;
  const {form, editingSession, editAllFutureMode, gpxFile} = event;
  callbacks.setSaving(true);

  if (form['__action'] === 'removeGpx') {
    service.deleteSessionGpx(clubId, editingSession.id).subscribe({
      next: () => {
        callbacks.setSaving(false);
        callbacks.reload();
      },
      error: () => callbacks.setSaving(false),
    });
    return;
  }

  if (editAllFutureMode && editingSession.recurringTemplateId) {
    const data = toRecurringSessionDtoFromEdit(form);
    service
      .updateRecurringTemplateWithInstances(clubId, editingSession.recurringTemplateId, data)
      .subscribe({
        next: () => {
          service.loadRecurringTemplates(clubId);
          callbacks.onDone();
        },
        error: () => callbacks.setSaving(false),
      });
    return;
  }

  const data = toSessionDto(form, 'SCHEDULED');
  const editId = editingSession.id;
  service.updateSession(clubId, editId, data).subscribe({
    next: () => uploadGpxThen(service, clubId, editId, gpxFile, callbacks.onDone),
    error: () => callbacks.setSaving(false),
  });
}

export function submitCreateSingle(
  service: ClubSessionService,
  clubId: string,
  event: SingleSessionCreateEvent,
  canCreateRecurring: boolean,
  callbacks: SessionSaveCallbacks,
): void {
  callbacks.setSaving(true);
  const category: 'OPEN' | 'SCHEDULED' = canCreateRecurring ? 'SCHEDULED' : 'OPEN';
  const data = toSessionDto(event.form, category);
  service.createSession(clubId, data).subscribe({
    next: (session) => uploadGpxThen(service, clubId, session.id, event.gpxFile, callbacks.onDone),
    error: () => callbacks.setSaving(false),
  });
}

export function submitCreateRecurring(
  service: ClubSessionService,
  clubId: string,
  event: RecurringTemplateCreateEvent,
  callbacks: SessionSaveCallbacks,
): void {
  callbacks.setSaving(true);
  const data = toRecurringSessionDto(event.form);
  service.createRecurringTemplate(clubId, data).subscribe({
    next: () => {
      service.loadRecurringTemplates(clubId);
      callbacks.onDone();
    },
    error: () => callbacks.setSaving(false),
  });
}

function uploadGpxThen(
  service: ClubSessionService,
  clubId: string,
  sessionId: string,
  gpxFile: File | null,
  done: () => void,
): void {
  if (!gpxFile) {
    done();
    return;
  }
  service.uploadSessionGpx(clubId, sessionId, gpxFile).subscribe({
    next: () => done(),
    error: () => done(),
  });
}

export interface SessionActionCallbacks {
  reload: () => void;
  afterChange?: () => void;
}

/** Coalesces the join / leave / duplicate / unlink calls — each is a service call then reload. */
export function runSessionAction(
  obs: { subscribe: (handlers: {next: () => void; error?: (err: unknown) => void}) => unknown },
  cb: SessionActionCallbacks,
  errorLabel?: string,
): void {
  obs.subscribe({
    next: () => {
      cb.reload();
      cb.afterChange?.();
    },
    error: (err) => {
      if (errorLabel) console.error(errorLabel, err);
    },
  });
}

export function unlinkSessionTraining(
  service: ClubSessionService,
  clubId: string,
  session: ClubTrainingSession,
  glt: GroupLinkedTraining,
  cb: SessionActionCallbacks,
): void {
  runSessionAction(
    service.unlinkTrainingFromSession(clubId, session.id, glt.clubGroupId || undefined),
    cb,
  );
}

/**
 * Confirms a session cancellation — handles both single-session and recurring-template cases.
 */
export function confirmSessionCancellation(
  service: ClubSessionService,
  clubId: string,
  target: ClubTrainingSession,
  mode: 'single' | 'all',
  cancelReason: string,
  cb: SessionActionCallbacks & {onClose: () => void},
): void {
  const reason = cancelReason || undefined;
  if (mode === 'all' && target.recurringTemplateId) {
    service.cancelRecurringSessions(clubId, target.recurringTemplateId, reason).subscribe({
      next: () => {
        service.loadRecurringTemplates(clubId);
        cb.onClose();
        cb.reload();
        cb.afterChange?.();
      },
      error: () => {},
    });
    return;
  }
  service.cancelEntireSession(clubId, target.id, reason).subscribe({
    next: () => {
      cb.onClose();
      cb.reload();
      cb.afterChange?.();
    },
    error: () => {},
  });
}
