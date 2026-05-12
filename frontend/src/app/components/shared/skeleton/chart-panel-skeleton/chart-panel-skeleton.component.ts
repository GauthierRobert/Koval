import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-chart-panel-skeleton',
  standalone: true,
  templateUrl: './chart-panel-skeleton.component.html',
  styleUrl: './chart-panel-skeleton.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartPanelSkeletonComponent {
  @Input() height = '280px';
  @Input() showHeader = true;
  @Input() headerWidth = '40%';
}
