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

      <!-- CYCLING: two frame lines + two wheels -->
      <ng-container *ngIf="sport === 'CYCLING'">
        <circle cx="6" cy="17" r="3.5"/>                  <!-- rear wheel -->
        <circle cx="18" cy="17" r="3.5"/>                 <!-- front wheel -->
        <path d="M6 17 L11 8 L18 17"/>                    <!-- frame line 1: seat-tube + chain-stay -->
        <path d="M11 8 L17 13"/>                          <!-- frame line 2: top-tube -->
        <path d="M9 8 L13 8"/>                            <!-- saddle -->
        <path d="M17 13 L20 11"/>                         <!-- handlebar -->
      </ng-container>

      <!-- RUNNING: three lines + legs with shoes -->
      <ng-container *ngIf="sport === 'RUNNING'">
        <circle cx="14" cy="4" r="1.5"/>                  <!-- head -->
        <path d="M14 5.5 L12 12"/>                        <!-- line 1: torso -->
        <path d="M13 8 L17 6"/>                           <!-- line 2: forward arm -->
        <path d="M13 8 L9 10"/>                           <!-- line 3: back arm -->
        <path d="M12 12 L15 17 L18 18"/>                  <!-- front leg + shoe -->
        <path d="M12 12 L10 17 L7 19"/>                   <!-- back leg + shoe -->
      </ng-container>

      <!-- SWIMMING: one body line + sea wave -->
      <ng-container *ngIf="sport === 'SWIMMING'">
        <circle cx="19" cy="7" r="2"/>                    <!-- head -->
        <path d="M3 11 C7 8 13 9 17 10"/>                 <!-- body: one stretched line -->
        <path d="M2 16 Q5.5 13 9 16 Q12.5 19 16 16 Q19.5 13 22 15"/> <!-- sea wave -->
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
