import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ClubDetail } from '../../../../../../../models/club.model';
import {
  ClubTestService,
  ClubTestSummary,
  CreateClubTestRequest,
} from '../../../../../../../services/club-test.service';
import { TestFormModalComponent } from '../test-form-modal/test-form-modal.component';

@Component({
  selector: 'app-test-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TestFormModalComponent],
  templateUrl: './test-list.component.html',
  styleUrl: './test-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestListComponent {
  @Input() club!: ClubDetail;
  @Input() tests: ClubTestSummary[] = [];
  @Input() isCoach = false;
  @Input() loading = false;
  @Output() open = new EventEmitter<string>();

  private readonly testService = inject(ClubTestService);

  showCreateModal = false;

  startCreate(): void {
    this.showCreateModal = true;
  }

  cancelCreate(): void {
    this.showCreateModal = false;
  }

  saveCreated(req: CreateClubTestRequest): void {
    this.testService.createTest(this.club.id, req).subscribe({
      next: () => (this.showCreateModal = false),
    });
  }

  trackById(_index: number, t: ClubTestSummary): string {
    return t.id;
  }
}
