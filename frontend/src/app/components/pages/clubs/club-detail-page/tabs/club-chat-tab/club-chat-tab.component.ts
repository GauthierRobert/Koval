import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EmbeddedChatComponent } from '../../../../../shared/embedded-chat/embedded-chat.component';

/**
 * Club detail page "Chat" tab. Thin wrapper around {@link EmbeddedChatComponent}
 * with the scope pre-set to CLUB.
 */
@Component({
  selector: 'app-club-chat-tab',
  standalone: true,
  imports: [CommonModule, EmbeddedChatComponent],
  template: `
    <div class="club-chat-tab">
      <app-embedded-chat scope="CLUB" [clubId]="clubId" [showHeader]="true"></app-embedded-chat>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .club-chat-tab {
        height: calc(100vh - 320px);
        min-height: 520px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubChatTabComponent {
  @Input({ required: true }) clubId!: string;
}
