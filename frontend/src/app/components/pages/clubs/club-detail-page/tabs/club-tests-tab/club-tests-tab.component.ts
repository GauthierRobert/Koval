import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { canManageClub, ClubDetail } from '../../../../../../models/club.model';
import { ClubTestService } from '../../../../../../services/club-test.service';
import { TestListComponent } from './test-list/test-list.component';
import { TestDetailComponent } from './test-detail/test-detail.component';

type ViewMode = 'list' | 'detail';

@Component({
  selector: 'app-club-tests-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, TestListComponent, TestDetailComponent],
  templateUrl: './club-tests-tab.component.html',
  styleUrl: './club-tests-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubTestsTabComponent {
  @Input() club!: ClubDetail;

  private readonly testService = inject(ClubTestService);

  view: ViewMode = 'list';
  selectedTestId: string | null = null;

  readonly tests$ = this.testService.tests$;
  readonly loading$ = this.testService.loading$;

  openTest(testId: string): void {
    this.selectedTestId = testId;
    this.view = 'detail';
    this.testService.loadTestDetail(this.club.id, testId);
    this.testService.loadIterations(this.club.id, testId);
  }

  backToList(): void {
    this.view = 'list';
    this.selectedTestId = null;
    this.testService.resetDetail();
  }

  isCoach(): boolean {
    return canManageClub(this.club?.currentMemberRole);
  }
}
