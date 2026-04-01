import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ClubGroup, ClubMember, ClubTrainingSession} from '../../../../../../../services/club.service';
import {
  MeetingPoint,
  MeetingPointPickerComponent,
} from '../../../../../../shared/meeting-point-picker/meeting-point-picker.component';

export interface SessionFormSaveEvent {
  form: Record<string, any>;
  editingSession: ClubTrainingSession | null;
  editAllFutureMode: boolean;
  gpxFile: File | null;
}

@Component({
  selector: 'app-session-form-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, MeetingPointPickerComponent],
  templateUrl: './session-form-modal.component.html',
  styleUrl: './session-form-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionFormModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() isSaving = false;
  @Input() editingSession: ClubTrainingSession | null = null;
  @Input() editAllFutureMode = false;
  @Input() clubGroups: ClubGroup[] = [];
  @Input() coachMembers: ClubMember[] = [];
  @Input() sports: readonly string[] = [];
  @Input() daysOfWeek: readonly string[] = [];

  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<SessionFormSaveEvent>();

  form: Record<string, any> = {};
  gpxFile: File | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.initForm();
    }
  }

  private initForm(): void {
    this.gpxFile = null;
    if (this.editingSession) {
      this.form = {
        title: this.editingSession.title,
        sport: this.editingSession.sport || 'CYCLING',
        scheduledAt: this.editingSession.scheduledAt
          ? this.toDatetimeLocal(this.editingSession.scheduledAt)
          : '',
        location: this.editingSession.location || '',
        meetingPointLat: this.editingSession.meetingPointLat ?? null,
        meetingPointLon: this.editingSession.meetingPointLon ?? null,
        description: this.editingSession.description || '',
        maxParticipants: this.editingSession.maxParticipants || '',
        durationMinutes: this.editingSession.durationMinutes || '',
        clubGroupId: this.editingSession.clubGroupId || '',
        responsibleCoachId: this.editingSession.responsibleCoachId || '',
        openToAll: this.editingSession.openToAll || false,
        openToAllDelayValue: this.editingSession.openToAllDelayValue || 2,
        openToAllDelayUnit: this.editingSession.openToAllDelayUnit || 'DAYS',
      };
    } else {
      this.form = {
        sport: 'CYCLING',
        title: '',
        clubGroupId: '',
        openToAll: false,
        openToAllDelayValue: 2,
        openToAllDelayUnit: 'DAYS',
      };
    }
  }

  toDatetimeLocal(isoStr: string): string {
    const d = new Date(isoStr);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  onMeetingPointChanged(point: MeetingPoint | null): void {
    this.form['meetingPointLat'] = point?.lat ?? null;
    this.form['meetingPointLon'] = point?.lon ?? null;
  }

  onGpxFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.gpxFile = input.files?.[0] ?? null;
  }

  removeGpx(): void {
    this.saved.emit({
      form: {__action: 'removeGpx'},
      editingSession: this.editingSession,
      editAllFutureMode: this.editAllFutureMode,
      gpxFile: null,
    });
  }

  get isFormValid(): boolean {
    if (!this.form['title'] || !this.form['sport']) return false;
    if (!this.editingSession && (!this.form['dayOfWeek'] || !this.form['timeOfDay'])) return false;
    return true;
  }

  close(): void {
    this.closed.emit();
  }

  save(): void {
    if (!this.isFormValid) return;
    this.saved.emit({
      form: {...this.form},
      editingSession: this.editingSession,
      editAllFutureMode: this.editAllFutureMode,
      gpxFile: this.gpxFile,
    });
  }
}
