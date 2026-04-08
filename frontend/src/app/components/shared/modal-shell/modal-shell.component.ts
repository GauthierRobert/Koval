import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  HostListener,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { A11yModule } from '@angular/cdk/a11y';
import { TranslateModule } from '@ngx-translate/core';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

@Component({
  selector: 'app-modal-shell',
  standalone: true,
  imports: [CommonModule, A11yModule, TranslateModule],
  templateUrl: './modal-shell.component.html',
  styleUrl: './modal-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModalShellComponent {
  @Input() title = '';
  @Input() size: ModalSize = 'md';
  @Input() closeOnBackdrop = true;
  @Input() showCloseButton = true;
  @Output() closed = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onBackdropClick(): void {
    if (this.closeOnBackdrop) {
      this.closed.emit();
    }
  }

  onPanelClick(event: Event): void {
    event.stopPropagation();
  }
}
