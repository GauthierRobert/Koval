import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';

export interface FormStats {
  ctl: number | null;
  atl: number | null;
  tsb: number | null;
  ftp: number | null;
}

@Component({
  selector: 'app-dashboard-performance-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './dashboard-performance-panel.component.html',
  styleUrl: './dashboard-performance-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPerformancePanelComponent {
  @Input() formStats: FormStats | null = null;
}
