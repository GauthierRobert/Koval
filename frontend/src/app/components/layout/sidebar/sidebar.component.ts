import {ChangeDetectionStrategy, Component} from '@angular/core';
import {TrainingHistoryComponent} from '../training-history/training-history.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [TrainingHistoryComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent {}
