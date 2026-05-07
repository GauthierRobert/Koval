import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ClubDetail } from '../../../../../../../models/club.model';
import { SPORT_BANNER_COLORS } from '../../../../../../../models/plan.model';
import {
  ClubTestDetail,
  ClubTestIteration,
  ClubTestResult,
  ClubTestService,
  CreateIterationRequest,
  RecordResultRequest,
  TestSegment,
  UpdateClubTestRequest,
} from '../../../../../../../services/club-test.service';
import { TestFormModalComponent } from '../test-form-modal/test-form-modal.component';
import { ResultEntryModalComponent } from '../result-entry-modal/result-entry-modal.component';
import { ApplyReferencesConfirmModalComponent } from '../apply-references-confirm-modal/apply-references-confirm-modal.component';
import { AuthService } from '../../../../../../../services/auth.service';

@Component({
  selector: 'app-test-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    TestFormModalComponent,
    ResultEntryModalComponent,
    ApplyReferencesConfirmModalComponent,
  ],
  templateUrl: './test-detail.component.html',
  styleUrl: './test-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestDetailComponent implements OnChanges {
  @Input() club!: ClubDetail;
  @Input() testId!: string;
  @Input() isCoach = false;
  @Output() back = new EventEmitter<void>();

  private readonly testService = inject(ClubTestService);
  private readonly authService = inject(AuthService);

  readonly test$ = this.testService.selectedTest$;
  readonly iterations$ = this.testService.iterations$;
  readonly selectedIterationId$ = this.testService.selectedIterationId$;
  readonly results$ = this.testService.results$;

  currentUserId: string | null = null;

  showEditModal = false;
  showResultModal = false;
  showApplyModal = false;
  resultModalResult: ClubTestResult | null = null;
  resultModalRecordingFor: string | null = null;
  applyModalResult: ClubTestResult | null = null;
  newIterationLabel = '';

  constructor() {
    this.authService.user$.subscribe((u) => (this.currentUserId = u?.id ?? null));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['testId']) {
      this.testService.loadTestDetail(this.club.id, this.testId);
      this.testService.loadIterations(this.club.id, this.testId);
    }
  }

  onIterationChange(iterationId: string): void {
    this.testService.selectIteration(this.club.id, this.testId, iterationId || null);
  }

  startNewIteration(): void {
    if (!this.newIterationLabel.trim()) return;
    const req: CreateIterationRequest = {
      label: this.newIterationLabel.trim(),
      startDate: new Date().toISOString().substring(0, 10),
      endDate: null,
      closeCurrent: true,
    };
    this.testService.startIteration(this.club.id, this.testId, req).subscribe({
      next: () => (this.newIterationLabel = ''),
    });
  }

  closeIteration(iteration: ClubTestIteration): void {
    if (!confirm(`Close iteration "${iteration.label}"?`)) return;
    this.testService.closeIteration(this.club.id, this.testId, iteration.id).subscribe();
  }

  openRecordOwn(): void {
    this.resultModalResult = null;
    this.resultModalRecordingFor = null;
    this.showResultModal = true;
  }

  openRecordFor(athleteId: string): void {
    this.resultModalResult = null;
    this.resultModalRecordingFor = athleteId;
    this.showResultModal = true;
  }

  openEditExisting(result: ClubTestResult): void {
    this.resultModalResult = result;
    this.resultModalRecordingFor = result.athleteId;
    this.showResultModal = true;
  }

  saveResult(req: RecordResultRequest, iterationId: string): void {
    this.testService.recordResult(this.club.id, this.testId, iterationId, req).subscribe({
      next: () => (this.showResultModal = false),
    });
  }

  openApply(result: ClubTestResult): void {
    this.applyModalResult = result;
    this.showApplyModal = true;
  }

  applyReferences(ruleIds: string[], iterationId: string): void {
    if (!this.applyModalResult) return;
    this.testService
      .applyReferences(this.club.id, this.testId, iterationId, this.applyModalResult.id, ruleIds)
      .subscribe({
        next: () => {
          this.showApplyModal = false;
          this.applyModalResult = null;
        },
      });
  }

  saveEdited(req: UpdateClubTestRequest): void {
    this.testService.updateTest(this.club.id, this.testId, req).subscribe({
      next: () => (this.showEditModal = false),
    });
  }

  archive(): void {
    if (!confirm('Archive this test?')) return;
    this.testService.archiveTest(this.club.id, this.testId).subscribe({
      next: () => this.back.emit(),
    });
  }

  ownResult(results: ClubTestResult[]): ClubTestResult | undefined {
    if (!this.currentUserId) return undefined;
    return results.find((r) => r.athleteId === this.currentUserId);
  }

  visibleResults(test: ClubTestDetail | null, results: ClubTestResult[]): ClubTestResult[] {
    if (!test) return [];
    if (this.isCoach || test.competitionMode) return results;
    if (!this.currentUserId) return [];
    return results.filter((r) => r.athleteId === this.currentUserId);
  }

  selectedIteration(iterations: ClubTestIteration[], id: string | null): ClubTestIteration | undefined {
    return iterations.find((i) => i.id === id) ?? undefined;
  }

  trackByResultId(_index: number, r: ClubTestResult): string {
    return r.id;
  }

  sportColor(sport: string): { bg: string; border: string; text: string } {
    return (
      SPORT_BANNER_COLORS[sport] ?? { bg: 'rgba(255,157,0,0.15)', border: '#ff9d00', text: '#ff9d00' }
    );
  }

  formatSegmentValue(seg: TestSegment, value: number | undefined): string {
    if (value === undefined || value === null) return '—';
    if (seg.resultUnit === 'SECONDS' || seg.resultUnit === 'PACE_S_PER_KM' || seg.resultUnit === 'PACE_S_PER_100M') {
      return this.formatSeconds(value);
    }
    if (seg.resultUnit === 'WATTS') return `${Math.round(value)} W`;
    if (seg.resultUnit === 'METERS') return `${Math.round(value)} m`;
    return value.toString();
  }

  private formatSeconds(totalSeconds: number): string {
    const total = Math.round(totalSeconds);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
  }
}
