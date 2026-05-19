import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ModalShellComponent } from '../../../shared/modal-shell/modal-shell.component';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { SavedSession } from '../../../../services/history.service';
import { formatTimeText } from '../../../shared/format/format.utils';

/**
 * Final state of the group after the user confirms.
 * - `memberIds`: every session that should share the same group (always includes the anchor).
 * - `groupId`: id to write to those sessions. Empty `memberIds` means "unlink the anchor"
 *   and `groupId` will be the existing one being cleared.
 */
export interface LinkChange {
  groupId: string;
  memberIds: string[];
  /** Sessions previously in the same group that the user removed — their groupId should be cleared. */
  removedIds: string[];
}

@Component({
  selector: 'app-link-sessions-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, ModalShellComponent, SportIconComponent],
  templateUrl: './link-sessions-modal.component.html',
  styleUrl: './link-sessions-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkSessionsModalComponent implements OnChanges {
  @Input() open = false;
  @Input() anchor: SavedSession | null = null;
  /** Other sessions completed on the same calendar day as the anchor. */
  @Input() candidates: SavedSession[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() apply = new EventEmitter<LinkChange>();

  selected = new Set<string>();
  submitting = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.seedSelection();
    }
  }

  private seedSelection(): void {
    this.selected = new Set<string>();
    const gid = this.anchor?.groupId;
    if (gid) {
      for (const c of this.candidates) {
        if (c.groupId === gid) this.selected.add(c.id);
      }
    }
    this.submitting = false;
  }

  toggle(sessionId: string): void {
    if (this.selected.has(sessionId)) {
      this.selected.delete(sessionId);
    } else {
      this.selected.add(sessionId);
    }
  }

  formatTime(seconds: number): string {
    return formatTimeText(seconds);
  }

  formatHourMinute(date: Date | string): string {
    return new Date(date).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  /** Sessions in the previous group that the user has un-checked. */
  private removedFromOldGroup(): string[] {
    if (!this.anchor?.groupId) return [];
    const oldGid = this.anchor.groupId;
    return this.candidates
      .filter((c) => c.groupId === oldGid && !this.selected.has(c.id))
      .map((c) => c.id);
  }

  onSubmit(): void {
    if (!this.anchor) return;
    this.submitting = true;
    const memberIds = this.selected.size > 0 ? [this.anchor.id, ...Array.from(this.selected)] : [];
    // Reuse the existing groupId when keeping the same anchor group, otherwise mint one from the anchor's own id.
    const groupId = this.anchor.groupId ?? this.anchor.id;
    this.apply.emit({
      groupId,
      memberIds,
      removedIds: this.removedFromOldGroup(),
    });
  }

  finish(): void {
    this.submitting = false;
    this.closed.emit();
  }
}
