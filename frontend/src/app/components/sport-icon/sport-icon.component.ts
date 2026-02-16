import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-sport-icon',
    standalone: true,
    imports: [CommonModule],
    template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      [attr.stroke]="color || 'currentColor'"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="className">

      <!-- CYCLING -->
      <ng-container *ngIf="sport === 'CYCLING'">
        <circle cx="5.5" cy="17.5" r="3.5"/>
        <circle cx="18.5" cy="17.5" r="3.5"/>
        <path d="M15 6h-5l-3 11.5"/>
        <path d="M9 17.5h.5a2 2 0 0 0 1.5-2l2.5-10"/> <!-- Frame -->
        <path d="M15 6a2 2 0 0 0 2 2h3"/> <!-- Handlebars -->
        <circle cx="15" cy="5" r="1"/> <!-- Head -->
        <path d="M12 17.5V14l-3-3 4-3 2 3h2"/> <!-- Body -->
      </ng-container>

      <!-- RUNNING -->
      <ng-container *ngIf="sport === 'RUNNING'">
        <path d="M4 16l2-1 2.5-4h3l2.8 5.7c.2.4.6.7 1.1.7H20"/> <!-- Back Leg -->
        <path d="M8 8l-2 3-3 4.5"/> <!-- Front Arm -->
        <path d="M13 7l2-3h3"/> <!-- Back Arm -->
        <path d="M13 7v6l2 3"/> <!-- Front Leg / Body -->
        <circle cx="13" cy="4" r="1.5"/> <!-- Head -->
      </ng-container>

      <!-- SWIMMING -->
      <ng-container *ngIf="sport === 'SWIMMING'">
        <path d="M2 12h20"/> <!-- Water -->
        <path d="M2 16h20"/> <!-- Water -->
        <path d="M2 8c.5-1 2-2 4-2s3.5 1 4 2 2 2 4 2 2-1 4-2 3.5-1 4-2"/> <!-- Splash/Arm -->
        <circle cx="17" cy="5" r="1.5"/> <!-- Head -->
        <path d="M13 9l2-2 2 2"/> <!-- Arm -->
      </ng-container>

      <!-- BRICK (Stack/Layers) -->
      <ng-container *ngIf="sport === 'BRICK'">
        <path d="M12 2L2 7l10 5 10-5-10-5z"/>
        <path d="M2 17l10 5 10-5"/>
        <path d="M2 12l10 5 10-5"/>
      </ng-container>

      <!-- GYM (Dumbbell) -->
      <ng-container *ngIf="sport === 'GYM'">
        <path d="M6.5 6.5l11 11"/>
        <path d="M21 21l-1 1"/>
        <path d="M3 3l1-1"/>
        <path d="M18 22l4-4"/>
        <path d="M2 6l4-4"/>
        <path d="M3 10l7-7"/>
        <path d="M14 21l7-7"/>
      </ng-container>
    </svg>
  `,
    styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
  `]
})
export class SportIconComponent {
    @Input() sport: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM' = 'CYCLING';
    @Input() size: number = 24;
    @Input() color: string = '';
    @Input() className: string = '';
}
