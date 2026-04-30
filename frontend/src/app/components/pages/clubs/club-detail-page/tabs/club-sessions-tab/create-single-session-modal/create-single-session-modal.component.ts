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
import {ClubGroup, ClubMember} from '../../../../../../../services/club.service';
import {
  MeetingPoint,
  MeetingPointPickerComponent,
} from '../../../../../../shared/meeting-point-picker/meeting-point-picker.component';

export interface SingleSessionCreateEvent {
  form: Record<string, any>;
  gpxFile: File | null;
}

@Component({
  selector: 'app-create-single-session-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, MeetingPointPickerComponent],
  templateUrl: './create-single-session-modal.component.html',
  styleUrl: './create-single-session-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateSingleSessionModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() isSaving = false;
  @Input() clubGroups: ClubGroup[] = [];
  @Input() coachMembers: ClubMember[] = [];
  @Input() sports: readonly string[] = [];

  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<SingleSessionCreateEvent>();

  form: Record<string, any> = {};
  gpxFile: File | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetForm();
    }
  }

  private resetForm(): void {
    this.gpxFile = null;
    this.form = {
      sport: 'CYCLING',
      title: '',
      clubGroupId: '',
      responsibleCoachId: '',
      openToAll: false,
      openToAllDelayValue: 2,
      openToAllDelayUnit: 'DAYS',
      scheduledAt: '',
      maxParticipants: '',
      durationMinutes: '',
      location: '',
      meetingPointLat: null,
      meetingPointLon: null,
      description: '',
    };
  }

  onMeetingPointChanged(point: MeetingPoint | null): void {
    this.form['meetingPointLat'] = point?.lat ?? null;
    this.form['meetingPointLon'] = point?.lon ?? null;
  }

  onGpxFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.gpxFile = input.files?.[0] ?? null;
  }

  get isFormValid(): boolean {
    return !!this.form['title'] && !!this.form['sport'] && !!this.form['scheduledAt'];
  }

  close(): void {
    this.closed.emit();
  }

  save(): void {
    if (!this.isFormValid) return;
    this.saved.emit({form: {...this.form}, gpxFile: this.gpxFile});
  }
}
