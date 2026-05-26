import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ClubTrainingSession } from '../../../../../../../services/club.service';

@Component({
  selector: 'app-cancel-session-dialogs',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './cancel-session-dialogs.component.html',
  styleUrl: './cancel-session-dialogs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CancelSessionDialogsComponent {
  @Input() showCancelRecurringChoice = false;
  @Input() showCancelConfirm = false;
  @Input() cancelMode: 'single' | 'all' = 'single';
  @Input() cancelTargetSession: ClubTrainingSession | null = null;
  @Input() showDeleteConfirm = false;
  @Input() deleteMode: 'single' | 'series' = 'single';
  @Input() deleteTargetSession: ClubTrainingSession | null = null;

  @Output() cancelThisOnly = new EventEmitter<void>();
  @Output() cancelAllFuture = new EventEmitter<void>();
  @Output() closeCancelRecurringChoice = new EventEmitter<void>();
  @Output() closeCancelConfirm = new EventEmitter<void>();
  @Output() confirmCancel = new EventEmitter<string>();
  @Output() requestDeleteSingle = new EventEmitter<void>();
  @Output() requestDeleteSeries = new EventEmitter<void>();
  @Output() confirmDelete = new EventEmitter<void>();
  @Output() closeDeleteConfirm = new EventEmitter<void>();

  cancelReason = '';

  onConfirmCancel(): void {
    this.confirmCancel.emit(this.cancelReason);
    this.cancelReason = '';
  }

  onCloseCancelConfirm(): void {
    this.cancelReason = '';
    this.closeCancelConfirm.emit();
  }
}
