import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Star button for marking a training as favorite. Filled when active, outline
 * when not — never hidden, so the affordance is always visible.
 *
 * Use `(toggled)` to receive click events; the host is responsible for the
 * actual state change (so the same star can be wired to either an optimistic
 * service call or to a plain in-memory toggle).
 */
@Component({
  selector: 'app-favorite-star',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './favorite-star.component.html',
  styleUrl: './favorite-star.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FavoriteStarComponent {
  @Input() active = false;
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() label = 'Toggle favorite';
  @Output() toggled = new EventEmitter<MouseEvent>();

  onClick(event: MouseEvent): void {
    event.stopPropagation();
    this.toggled.emit(event);
  }
}
