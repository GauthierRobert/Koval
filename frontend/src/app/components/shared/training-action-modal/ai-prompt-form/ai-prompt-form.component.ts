import {Component, ChangeDetectionStrategy, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ZoneSystem} from '../../../../services/zone';
import {ClubGroup} from '../../../../services/club.service';
import {SportFilter, SPORT_OPTIONS} from '../../../../models/training.model';

@Component({
  selector: 'app-ai-prompt-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './ai-prompt-form.component.html',
  styleUrl: './ai-prompt-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiPromptFormComponent {
  @Input() prompt = '';
  @Input() selectedSport: SportFilter = 'CYCLING';
  @Input() sportOptions: { label: string; value: SportFilter }[] = SPORT_OPTIONS;
  @Input() selectedZoneSystemId = '';
  @Input() filteredZoneSystems: ZoneSystem[] = [];
  @Input() availableGroups: ClubGroup[] = [];
  @Input() selectedGroupId = '';
  @Input() mode = '';
  @Input() clubId?: string;
  @Input() sessionAvailableGroups: ClubGroup[] = [];
  @Input() sessionShowNoGroupOption = true;
  @Input() loading = false;

  @Output() promptChange = new EventEmitter<string>();
  @Output() sportChange = new EventEmitter<SportFilter>();
  @Output() zoneSystemChange = new EventEmitter<string>();
  @Output() groupChange = new EventEmitter<string>();

  onPromptChange(value: string): void {
    this.promptChange.emit(value);
  }

  onSportChange(value: SportFilter): void {
    this.sportChange.emit(value);
  }

  onZoneSystemChange(value: string): void {
    this.zoneSystemChange.emit(value);
  }

  onGroupChange(value: string): void {
    this.groupChange.emit(value);
  }
}
