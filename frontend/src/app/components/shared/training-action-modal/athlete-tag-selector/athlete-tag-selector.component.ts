import {Component, ChangeDetectionStrategy, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {User} from '../../../../services/auth.service';

@Component({
  selector: 'app-athlete-tag-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './athlete-tag-selector.component.html',
  styleUrl: './athlete-tag-selector.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthleteTagSelectorComponent {
  @Input() preselectedAthletes: User[] = [];
  @Input() availableAthletes: User[] = [];
  @Input() availableTags: string[] = [];
  @Input() selectedAthleteIds: string[] = [];
  @Input() activeTags: Set<string> = new Set();
  @Input() showAthleteChip = false;
  @Input() showTagFilter = false;
  @Input() showAthleteSelect = false;
  @Input() loading = false;

  @Output() toggleAthlete = new EventEmitter<string>();
  @Output() toggleTag = new EventEmitter<string>();

  isAthleteSelected(id: string): boolean {
    return this.selectedAthleteIds.includes(id);
  }

  isTagActive(tag: string): boolean {
    return this.activeTags.has(tag);
  }

  onToggleAthlete(id: string): void {
    this.toggleAthlete.emit(id);
  }

  onToggleTag(tag: string): void {
    this.toggleTag.emit(tag);
  }
}
