import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

type Sport = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM';

const SPORT_COLORS: Record<Sport, string> = {
  CYCLING: 'var(--success-color)',
  RUNNING: 'var(--danger-color)',
  SWIMMING: 'var(--secondary-color)',
  BRICK: 'var(--accent-color)',
  GYM: 'var(--text-muted)',
};

@Component({
  selector: 'app-sport-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [style.color]="resolvedColor()"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="className()">
      @switch (sport()) {
        @case ('CYCLING') {
          <circle cx="6" cy="17" r="3.5"/>
          <circle cx="18" cy="17" r="3.5"/>
          <path d="M6 17 L11 8 L18 17"/>
          <path d="M11 8 L17 13"/>
          <path d="M9 8 L13 8"/>
          <path d="M17 13 L20 11"/>
        }
        @case ('RUNNING') {
          <circle cx="14" cy="4" r="1.5"/>
          <path d="M14 5.5 L12 12"/>
          <path d="M13 8 L17 6"/>
          <path d="M13 8 L9 10"/>
          <path d="M12 12 L15 17 L18 18"/>
          <path d="M12 12 L10 17 L7 19"/>
        }
        @case ('SWIMMING') {
          <circle cx="19" cy="7" r="2"/>
          <path d="M3 11 C7 8 13 9 17 10"/>
          <path d="M2 16 Q5.5 13 9 16 Q12.5 19 16 16 Q19.5 13 22 15"/>
        }
        @case ('BRICK') {
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        }
        @case ('GYM') {
          <path d="M6.5 6.5l11 11"/>
          <path d="M21 21l-1 1"/>
          <path d="M3 3l1-1"/>
          <path d="M18 22l4-4"/>
          <path d="M2 6l4-4"/>
          <path d="M3 10l7-7"/>
          <path d="M14 21l7-7"/>
        }
      }
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
  readonly sport = input<Sport>('CYCLING');
  readonly size = input<number>(24);
  readonly color = input<string>('');
  readonly className = input<string>('');

  readonly resolvedColor = computed(() => this.color() || SPORT_COLORS[this.sport()] || 'currentColor');
}
